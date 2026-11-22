package com.focusguard.app.pureshield

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PureShieldMediaWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val scanner = OfflineNsfwScanner(applicationContext)
        try {
            if (!scanner.isModelInstalled()) return@withContext Result.success()
            MediaLibraryScanner.scanRecent(applicationContext, scanner)
            Result.success()
        } catch (_: SecurityException) {
            Result.failure()
        } catch (_: Exception) {
            Result.retry()
        } finally {
            scanner.close()
        }
    }
}
