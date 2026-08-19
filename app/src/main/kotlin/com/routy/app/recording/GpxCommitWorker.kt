package com.routy.app.recording

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.routy.app.core.GpxUploadNotifier
import com.routy.app.core.GpxQueueNotifier
import com.routy.app.core.network.ApiClientProvider
import com.routy.app.core.storage.GpxCommitQueueStore
import com.routy.app.core.storage.SecureStorage
import com.routy.app.logic.recording.GpxCommitOutcome
import com.routy.app.logic.recording.gpxCommitOutcome
import java.io.IOException

class GpxCommitWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val commitId = inputData.getString(KEY_COMMIT_ID) ?: return Result.failure()
        val queueStore = GpxCommitQueueStore(applicationContext)
        val pending = queueStore.load(commitId) ?: return Result.success()
        if (pending.permanentFailure) return Result.success()

        if (SecureStorage(applicationContext).serverUrl.isNullOrBlank()) {
            return Result.retry()
        }

        val response = try {
            ApiClientProvider(SecureStorage(applicationContext)).service.commitGpx(pending.request)
        } catch (_: IOException) {
            return Result.retry()
        }

        return when (gpxCommitOutcome(response.isSuccessful, response.code())) {
            GpxCommitOutcome.Success -> {
                queueStore.remove(commitId)
                GpxQueueNotifier.setCounts(queueStore.listPending().size, queueStore.listFailed().size)
                GpxUploadNotifier.notifyUploadSucceeded()
                Result.success()
            }
            GpxCommitOutcome.Retry -> Result.retry()
            GpxCommitOutcome.PermanentFailure -> {
                queueStore.markPermanentFailure(commitId, response.code())
                GpxQueueNotifier.setCounts(queueStore.listPending().size, queueStore.listFailed().size)
                GpxUploadNotifier.notifyUploadFailed(response.code())
                Result.success()
            }
        }
    }

    companion object {
        const val KEY_COMMIT_ID = "commit_id"

        fun workName(commitId: String) = "gpx_commit_$commitId"
    }
}
