package com.focusguard.app.pureshield

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.net.HttpURLConnection
import java.net.URL
import java.io.FileOutputStream
import kotlin.math.exp

/**
 * Offline image classifier for the Apache-2.0 Marqo NSFW Image Detection 384 ONNX export.
 * Expected asset: app/src/main/assets/models/nsfw-image-detection-384.onnx
 * Input: RGB NCHW float32 [1,3,384,384], pixels normalized to [-1,1].
 * Output: logits [1,2], class order [nsfw, safe].
 */
class OfflineNsfwScanner(private val context: Context) : AutoCloseable {
    companion object {
        const val MODEL_ASSET = "models/nsfw-image-detection-384.onnx"
        const val INPUT_SIZE = 384
        const val DEFAULT_QUARANTINE_THRESHOLD = 0.80f
        const val MODEL_URL = "https://huggingface.co/kasimtmc/nsfw-image-detection-384-onnx/resolve/main/nsfw_vit_tiny_384_int8.onnx"
        const val MODEL_SHA256 = "20cc99a8986320972273253325975203f2a647ca0010a1ac9c869a0dbb9eb77d"
    }

    data class ScanResult(
        val nsfwScore: Float,
        val safeScore: Float,
        val flagged: Boolean,
        val modelName: String = "Marqo NSFW Image Detection 384"
    )

    class ModelMissingException : Exception(
        "AI model is not installed. Tap Download AI Model once while online; scanning works offline afterward."
    )

    fun modelFile(): File = File(File(context.filesDir, "pureshield-models"), "nsfw-image-detection-384.onnx")

    fun isModelInstalled(): Boolean = try {
        modelFile().isFile && sha256(modelFile()) == MODEL_SHA256
    } catch (_: Exception) { false }

    /** Download once; inference itself remains fully on-device and offline. */
    fun downloadModel(onProgress: (Int) -> Unit = {}): File {
        val target = modelFile()
        if (target.exists() && sha256(target) == MODEL_SHA256) return target
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "model.download")
        val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000
            readTimeout = 60000
            requestMethod = "GET"
            instanceFollowRedirects = true
        }
        try {
            connection.connect()
            require(connection.responseCode in 200..299) { "Model download failed: HTTP ${connection.responseCode}" }
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        copied += count
                        if (total > 0) onProgress(((copied * 100) / total).toInt().coerceIn(0, 100))
                    }
                    output.fd.sync()
                }
            }
        } finally {
            connection.disconnect()
        }
        require(sha256(temp) == MODEL_SHA256) { "Downloaded model checksum mismatch; file rejected." }
        if (target.exists()) target.delete()
        check(temp.renameTo(target)) { "Could not install AI model file." }
        onProgress(100)
        return target
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private val environment: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null

    @Synchronized
    private fun getSession(): OrtSession {
        session?.let { return it }
        val modelBytes = when {
            modelFile().exists() && isModelInstalled() -> modelFile().readBytes()
            modelFile().exists() -> throw ModelMissingException()
            else -> try { context.assets.open(MODEL_ASSET).use { it.readBytes() } }
            catch (_: Exception) { throw ModelMissingException() }
        }
        val options = OrtSession.SessionOptions()
        options.setIntraOpNumThreads(2)
        val created = try {
            environment.createSession(modelBytes, options)
        } finally {
            options.close()
        }
        session = created
        return created
    }

    fun scan(uri: Uri, threshold: Float = DEFAULT_QUARANTINE_THRESHOLD): ScanResult {
        val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val sample = calculateSampleSize(bounds.outWidth, bounds.outHeight, 1600, 1600)
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeStream(stream, null, options)
        } ?: throw IllegalArgumentException("Unable to read selected image")

        return try {
            scan(bitmap, threshold)
        } finally {
            bitmap.recycle()
        }
    }

    fun scan(bitmap: Bitmap, threshold: Float = DEFAULT_QUARANTINE_THRESHOLD): ScanResult {
        val input = preprocess(bitmap)
        val model = getSession()
        val directBuffer = ByteBuffer.allocateDirect(input.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        directBuffer.put(input)
        directBuffer.rewind()
        val tensor = OnnxTensor.createTensor(
            environment,
            directBuffer,
            longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
        )
        tensor.use {
            val inputName = model.inputNames.firstOrNull() ?: "input"
            val outputName = model.outputNames.firstOrNull()
                ?: throw IllegalStateException("AI model has no output tensor")
            model.run(mapOf(inputName to tensor)).use { output ->
                val logits = output[outputName].orElseThrow().value as Array<FloatArray>
                require(logits.isNotEmpty() && logits[0].size >= 2) { "Unexpected model output shape" }
                val probabilities = softmax(logits[0])
                val nsfw = probabilities[0]
                val safe = probabilities[1]
                return ScanResult(
                    nsfwScore = nsfw,
                    safeScore = safe,
                    flagged = nsfw >= threshold.coerceIn(0.50f, 0.99f)
                )
            }
        }
    }

    private fun preprocess(source: Bitmap): FloatArray {
        val resized = Bitmap.createScaledBitmap(source, INPUT_SIZE, INPUT_SIZE, true)
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        if (resized !== source) resized.recycle()

        val plane = INPUT_SIZE * INPUT_SIZE
        val input = FloatArray(3 * plane)
        for (y in 0 until INPUT_SIZE) {
            for (x in 0 until INPUT_SIZE) {
                val pixel = pixels[y * INPUT_SIZE + x]
                val index = y * INPUT_SIZE + x
                input[index] = (((pixel shr 16) and 0xFF) / 127.5f) - 1f
                input[plane + index] = (((pixel shr 8) and 0xFF) / 127.5f) - 1f
                input[2 * plane + index] = ((pixel and 0xFF) / 127.5f) - 1f
            }
        }
        return input
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val max = logits.maxOrNull() ?: 0f
        val exps = FloatArray(logits.size) { exp((logits[it] - max).toDouble()).toFloat() }
        val sum = exps.sum().coerceAtLeast(1e-12f)
        return FloatArray(exps.size) { exps[it] / sum }
    }

    private fun calculateSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var sample = 1
        if (height > reqHeight || width > reqWidth) {
            var halfHeight = height / 2
            var halfWidth = width / 2
            while (halfHeight / sample >= reqHeight && halfWidth / sample >= reqWidth) sample *= 2
        }
        return sample.coerceAtLeast(1)
    }

    data class VideoScanResult(val nsfwScore: Float, val safeScore: Float, val flagged: Boolean, val framesScanned: Int)

    /** Sample video frames locally; never uploads or modifies the source video. */
    fun scanVideo(uri: Uri, threshold: Float = DEFAULT_QUARANTINE_THRESHOLD, intervalMs: Long = 3000L, maxDurationMs: Long = Long.MAX_VALUE): VideoScanResult {
        val retriever = android.media.MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            require(durationMs > 0L) { "Unable to read video duration" }
            val scanDurationMs = minOf(durationMs, maxDurationMs)
            var worstNsfw = 0f
            var bestSafe = 1f
            var frames = 0
            var time = 0L
            while (time < scanDurationMs) {
                val frame = retriever.getFrameAtTime(time * 1000L, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (frame != null) {
                    try {
                        val result = scan(frame, threshold)
                        worstNsfw = maxOf(worstNsfw, result.nsfwScore)
                        bestSafe = minOf(bestSafe, result.safeScore)
                        frames++
                    } finally { frame.recycle() }
                }
                time += intervalMs.coerceAtLeast(1000L)
            }
            return VideoScanResult(worstNsfw, bestSafe, worstNsfw >= threshold, frames)
        } finally { retriever.release() }
    }

    /** Copy a flagged selection into app-private storage; the original is never deleted. */
    fun quarantine(uri: Uri): File {
        val folder = File(context.filesDir, "pureshield-quarantine").apply { mkdirs() }
        val mime = context.contentResolver.getType(uri).orEmpty()
        val extension = when {
            mime.contains("video", true) -> "mp4"
            mime.contains("png", true) -> "png"
            mime.contains("webp", true) -> "webp"
            else -> "jpg"
        }
        val target = File(folder, "flagged_${System.currentTimeMillis()}.$extension")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open selected image" }
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return target
    }

    override fun close() {
        session?.close()
        session = null
    }
}
