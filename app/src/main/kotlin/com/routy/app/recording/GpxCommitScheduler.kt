package com.routy.app.recording

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.routy.app.core.storage.GpxCommitQueueStore
import com.routy.app.core.GpxQueueNotifier
import com.routy.app.logic.api.PendingGpxCommit
import com.routy.app.logic.api.GpxCommitRequest
import java.util.UUID
import java.util.concurrent.TimeUnit

class GpxCommitScheduler(
    private val context: Context,
    private val queueStore: GpxCommitQueueStore,
) {
    fun enqueue(request: GpxCommitRequest): String {
        queueStore.listPending().firstOrNull { it.request == request }?.let { existing ->
            refreshCounts()
            scheduleWork(existing.id)
            return existing.id
        }
        val id = UUID.randomUUID().toString()
        queueStore.enqueue(PendingGpxCommit(id = id, enqueuedAtMs = System.currentTimeMillis(), request = request))
        refreshCounts()
        scheduleWork(id)
        return id
    }

    fun schedulePending() {
        queueStore.listPending().forEach { scheduleWork(it.id) }
        refreshCounts()
    }

    private fun refreshCounts() {
        GpxQueueNotifier.setCounts(queueStore.listPending().size, queueStore.listFailed().size)
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
