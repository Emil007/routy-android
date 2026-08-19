package com.routy.app.core.storage

import android.content.Context
import com.routy.app.logic.api.PendingGpxCommit
import com.routy.app.logic.storage.GpxCommitQueueFileStore
import java.io.File

/** Durable queue for failed GPX commits — drained by [com.routy.app.recording.GpxCommitWorker]. */
class GpxCommitQueueStore(context: Context) {
    private val store = GpxCommitQueueFileStore(File(context.filesDir, "gpx_commit_queue"))

    fun enqueue(pending: PendingGpxCommit) = store.enqueue(pending)

    fun load(id: String): PendingGpxCommit? = store.load(id)

    fun listAll(): List<PendingGpxCommit> = store.listAll()

    fun listPending(): List<PendingGpxCommit> = store.listAll().filter { !it.permanentFailure }

    fun listFailed(): List<PendingGpxCommit> = store.listAll().filter { it.permanentFailure }

    fun markPermanentFailure(id: String, httpCode: Int) {
        val pending = load(id) ?: return
        store.enqueue(
            pending.copy(
                permanentFailure = true,
                failureHttpCode = httpCode,
                failedAtMs = System.currentTimeMillis(),
            ),
        )
    }

    fun remove(id: String) = store.remove(id)
}
