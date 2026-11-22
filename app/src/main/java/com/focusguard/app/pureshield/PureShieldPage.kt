package com.focusguard.app.pureshield

import android.Manifest
import android.net.Uri
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

@Composable
fun PureShieldPage(activity: ComponentActivity) {
    val context = activity.applicationContext
    val scanner = remember { OfflineNsfwScanner(context) }
    DisposableEffect(scanner) { onDispose { scanner.close() } }
    val scope = rememberCoroutineScope()
    var scanning by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var imageResult by remember { mutableStateOf<OfflineNsfwScanner.ScanResult?>(null) }
    var videoResult by remember { mutableStateOf<OfflineNsfwScanner.VideoScanResult?>(null) }
    var threshold by remember { mutableFloatStateOf(OfflineNsfwScanner.DEFAULT_QUARANTINE_THRESHOLD) }
    var quarantineFiles by remember { mutableStateOf(emptyList<File>()) }
    val prefs = remember { context.getSharedPreferences("pureshield_settings", 0) }
    var backgroundEnabled by remember { mutableStateOf(prefs.getBoolean("background_scan", false)) }
    val modelReady = remember { mutableStateOf(scanner.isModelInstalled()) }

    fun refreshQuarantine() {
        quarantineFiles = File(context.filesDir, "pureshield-quarantine").listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
        modelReady.value = scanner.isModelInstalled()
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scanning = true; error = null; imageResult = null; videoResult = null
            scope.launch {
                try {
                    val result = withContext(Dispatchers.IO) { scanner.scan(uri, threshold) }
                    imageResult = result
                    if (result.flagged) withContext(Dispatchers.IO) { scanner.quarantine(uri) }
                    refreshQuarantine()
                } catch (t: Throwable) { error = t.message ?: "Image scan failed" }
                finally { scanning = false }
            }
        }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scanning = true; error = null; imageResult = null; videoResult = null
            scope.launch {
                try {
                    val result = withContext(Dispatchers.IO) { scanner.scanVideo(uri, threshold, intervalMs = 2500L) }
                    videoResult = result
                    if (result.flagged) withContext(Dispatchers.IO) { scanner.quarantine(uri) }
                    refreshQuarantine()
                } catch (t: Throwable) { error = t.message ?: "Video scan failed" }
                finally { scanning = false }
            }
        }
    }

    val mediaPermissions = remember {
        when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) {
            scanning = true; error = null
            scope.launch {
                try {
                    val summary = withContext(Dispatchers.IO) { MediaLibraryScanner.scanRecent(context, scanner) }
                    status = "Gallery scan complete: ${summary.imagesScanned} images, ${summary.videosScanned} videos scanned; ${summary.flagged} flagged, ${summary.quarantined} quarantined."
                    refreshQuarantine()
                } catch (t: Throwable) { error = t.message ?: "Gallery scan failed" }
                finally { scanning = false }
            }
        } else error = "Media permission was not granted. You can still scan files selected with the picker."
    }

    LaunchedEffect(Unit) { refreshQuarantine() }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFF247BCE))
            Column(Modifier.weight(1f)) {
                Text("PURESHIELD AI", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("On-device adult-content screening", fontSize = 12.sp, color = Color.Gray)
            }
            Text(if (modelReady.value) "AI READY" else "MODEL NEEDED", color = if (modelReady.value) Color(0xFF16834A) else Color(0xFF9A6100), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }

        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Offline AI Model", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(if (modelReady.value) "Model installed. Scans run locally and work without internet." else "Download the verified 6.7 MB quantized model once. After installation, scanning runs offline.", fontSize = 12.sp, color = Color.Gray)
                Button(onClick = {
                    downloading = true; error = null
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { scanner.downloadModel() }
                            status = "AI model downloaded, checksum verified and installed. Offline scanning is ready."
                            refreshQuarantine()
                        } catch (t: Throwable) { error = t.message ?: "Model download failed" }
                        finally { downloading = false }
                    }
                }, enabled = !downloading && !modelReady.value, modifier = Modifier.fillMaxWidth()) {
                    if (downloading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Lock, contentDescription = null)
                    Text(if (downloading) "  Downloading model…" else if (modelReady.value) "  Model installed" else "  Download AI Model")
                }
                Text("Model: quantized Marqo NSFW/Safe classifier · Apache-2.0 · SHA-256 verification", fontSize = 10.sp, color = Color.Gray)
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Scan Content", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text("Choose individual media or scan recent items in your permitted media library. Flagged items are copied to private quarantine; originals are not deleted.", fontSize = 12.sp, color = Color.Gray)
                Button(onClick = { imagePicker.launch("image/*") }, enabled = modelReady.value && !scanning, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.ImageSearch, contentDescription = null); Text("  Scan an Image")
                }
                OutlinedButton(onClick = { videoPicker.launch("video/*") }, enabled = modelReady.value && !scanning, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.VideoLibrary, contentDescription = null); Text("  Scan a Video")
                }
                OutlinedButton(onClick = { permissionLauncher.launch(mediaPermissions) }, enabled = modelReady.value && !scanning, modifier = Modifier.fillMaxWidth()) {
                    Text("  Scan Recent Gallery Images & Videos")
                }
                Text("Quarantine threshold: ${(threshold * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Slider(value = threshold, onValueChange = { threshold = it }, valueRange = 0.60f..0.95f, enabled = !scanning)
                Text("Higher thresholds reduce some false alarms but may miss content. Test on your own device before relying on automatic filtering.", fontSize = 10.sp, color = Color.Gray)
            }
        }

        if (scanning || downloading) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                Text(if (downloading) "Downloading and verifying model…" else "Scanning locally on this device…", fontSize = 12.sp)
            }
        }
        imageResult?.let { result ->
            ResultCard("IMAGE RESULT", result.nsfwScore, result.safeScore, result.flagged)
        }
        videoResult?.let { result ->
            ResultCard("VIDEO RESULT · ${result.framesScanned} FRAMES", result.nsfwScore, result.safeScore, result.flagged)
        }
        status?.let { Text(it, fontSize = 11.sp, color = Color(0xFF16834A)) }
        error?.let { Text("Error: $it", fontSize = 11.sp, color = Color(0xFFB3261E)) }

        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Background gallery scan", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Switch(checked = backgroundEnabled, onCheckedChange = { enabled ->
                        backgroundEnabled = enabled
                        prefs.edit().putBoolean("background_scan", enabled).apply()
                        val work = WorkManager.getInstance(context)
                        if (enabled) {
                            val request = PeriodicWorkRequestBuilder<PureShieldMediaWorker>(12, TimeUnit.HOURS).build()
                            work.enqueueUniquePeriodicWork("pureshield-periodic-media-scan", ExistingPeriodicWorkPolicy.UPDATE, request)
                        } else work.cancelUniqueWork("pureshield-periodic-media-scan")
                    })
                }
                Text("When enabled, Android periodically checks newly added recent media (subject to media permissions and battery limits). The first scan can be started above.", fontSize = 10.sp, color = Color.Gray)
            }
        }

        if (quarantineFiles.isNotEmpty()) {
            Text("Private Quarantine (${quarantineFiles.size})", fontWeight = FontWeight.SemiBold)
            LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(quarantineFiles.take(20)) { file ->
                    Text("${file.name} · ${(file.length() / 1024)} KB", fontSize = 10.sp, color = Color.Gray)
                }
            }
        }
        Text("Limits: this classifier returns NSFW/Safe only, not separate nudity, suggestive and porn labels. Video scans sample frames (not every frame). Android background scanning is periodic, not instant. False positives and missed content are possible.", fontSize = 10.sp, color = Color.Gray)
    }
}

@Composable
private fun ResultCard(title: String, nsfw: Float, safe: Float, flagged: Boolean) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (flagged) "$title · FLAGGED" else "$title · NO HIGH-RISK SCORE", fontWeight = FontWeight.Bold, color = if (flagged) Color(0xFFB3261E) else Color(0xFF16834A))
            Text("NSFW: ${(nsfw * 100).toInt()}% · Safe: ${(safe * 100).toInt()}%", fontSize = 12.sp)
            Text("Scores are model outputs, not guaranteed probabilities.", fontSize = 10.sp, color = Color.Gray)
        }
    }
}
