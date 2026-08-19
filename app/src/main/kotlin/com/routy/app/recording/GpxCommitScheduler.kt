package com.routy.app.recording

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.routy.app.core.storage.GpxCommitQueueStore
import com.routy.app.core.storage.PendingGpxCommit
import com.routy.app.logic.api.GpxCommitRequest
import java.util.UUID
import java.util.concurrent.TimeUnit

class GpxCommitScheduler(
    private val context: Context,
    private val queueStore: GpxCommitQueueStore,
) {
    fun enqueue(request: GpxCommitRequest): String {
        val id = UUID.randomUUID().toString()
        queueStore.enqueue(PendingGpxCommit(id = id, enqueuedAtMs = System.currentTimeMillis(), request = request))
        scheduleWork(id)
        return id
    }

    fun schedulePending() {
        queueStore.listAll().forEach { scheduleWork(it.id) }
    }

    private fun scheduleWork(commitId: String) {
        val work = OneTimeWorkRequestBuilder<GpxCommitWorker>()
            .setInputData(androidx.work.workDataOf(GpxCommitWorker.KEY_COMMIT_ID to commitId))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            GpxCommitWorker.workName(commitId),
            ExistingWorkPolicy.KEEP,
            work,
        )
    }
}
