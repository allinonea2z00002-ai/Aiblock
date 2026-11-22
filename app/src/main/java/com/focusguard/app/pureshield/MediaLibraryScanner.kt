package com.focusguard.app.pureshield

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import android.net.Uri

/** Scans a bounded batch of recent local media. Source files are never deleted or modified. */
object MediaLibraryScanner {
    data class Summary(val imagesScanned: Int, val videosScanned: Int, val flagged: Int, val quarantined: Int)

    fun scanRecent(context: Context, scanner: OfflineNsfwScanner, imageLimit: Int = 80, videoLimit: Int = 12): Summary {
        val prefs = context.getSharedPreferences("pureshield_media_scan", Context.MODE_PRIVATE)
        val seen = prefs.getStringSet("seen_uris", emptySet())?.toMutableSet() ?: mutableSetOf()
        var images = 0
        var videos = 0
        var flagged = 0
        var quarantined = 0

        val imageProjection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_MODIFIED)
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            imageProjection, null, null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val modifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            while (cursor.moveToNext() && images < imageLimit) {
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(idIndex))
                val key = "$uri:${cursor.getLong(modifiedIndex)}"
                if (!seen.contains(key)) {
                    try {
                        val result = scanner.scan(uri)
                        images++
                        if (result.flagged) { flagged++; scanner.quarantine(uri); quarantined++ }
                        seen.add(key)
                    } catch (_: Exception) { /* Keep failed items eligible for a later retry. */ }
                }
            }
        }

        val videoProjection = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DATE_MODIFIED)
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            videoProjection, null, null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val modifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
            while (cursor.moveToNext() && videos < videoLimit) {
                val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cursor.getLong(idIndex))
                val key = "$uri:${cursor.getLong(modifiedIndex)}"
                if (!seen.contains(key)) {
                    try {
                        val result = scanner.scanVideo(uri, intervalMs = 5000L, maxDurationMs = 2 * 60 * 1000L)
                        videos++
                        if (result.flagged) { flagged++; scanner.quarantine(uri); quarantined++ }
                        seen.add(key)
                    } catch (_: Exception) { /* Keep failed items eligible for a later retry. */ }
                }
            }
        }
        // Keep preferences bounded so years of scans do not grow indefinitely.
        val bounded = seen.toList().takeLast(1500).toSet()
        prefs.edit().putStringSet("seen_uris", bounded).apply()
        return Summary(images, videos, flagged, quarantined)
    }
}
