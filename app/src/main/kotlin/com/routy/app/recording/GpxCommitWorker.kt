package com.routy.app.recording

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.routy.app.core.network.ApiClientProvider
import com.routy.app.core.storage.GpxCommitQueueStore
import com.routy.app.core.storage.SecureStorage
import java.io.IOException

class GpxCommitWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val commitId = inputData.getString(KEY_COMMIT_ID) ?: return Result.failure()
        val queueStore = GpxCommitQueueStore(applicationContext)
        val pending = queueStore.load(commitId) ?: return Result.success()

        if (SecureStorage(applicationContext).serverUrl.isNullOrBlank()) {
            return Result.retry()
        }

        val response = try {
            ApiClientProvider(SecureStorage(applicationContext)).service.commitGpx(pending.request)
        } catch (_: IOException) {
            return Result.retry()
        }

        return when {
            response.isSuccessful -> {
                queueStore.remove(commitId)
                Result.success()
            }
            response.code() in RETRYABLE_HTTP -> Result.retry()
            else -> {
                queueStore.remove(commitId)
                Result.failure()
            }
        }
    }

    companion object {
        const val KEY_COMMIT_ID = "commit_id"

        private val RETRYABLE_HTTP = 500..599

        fun workName(commitId: String) = "gpx_commit_$commitId"
    }
}
