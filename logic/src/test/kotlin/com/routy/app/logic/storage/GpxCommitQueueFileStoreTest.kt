package com.routy.app.logic.storage

import com.routy.app.logic.api.GpxCommitRequest
import com.routy.app.logic.api.GpxEndpoint
import com.routy.app.logic.api.GpxPoint
import com.routy.app.logic.api.GpxTrack
import com.routy.app.logic.api.PendingGpxCommit
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GpxCommitQueueFileStoreTest {
    private val tempDir = File.createTempFile("gpx-queue-test", null).apply {
        delete()
        mkdirs()
    }
    private val store = GpxCommitQueueFileStore(tempDir)

    @AfterTest
    fun cleanup() {
        tempDir.listFiles()?.forEach { it.delete() }
        tempDir.delete()
    }

    @Test
    fun enqueueLoadRemoveRoundTrip() {
        val pending = samplePending("a")
        store.enqueue(pending)
        assertEquals(pending, store.load("a"))
        store.remove("a")
        assertNull(store.load("a"))
    }

    @Test
    fun listAllSortedByEnqueuedAt() {
        store.enqueue(samplePending("b", enqueuedAtMs = 20L))
        store.enqueue(samplePending("a", enqueuedAtMs = 10L))
        assertEquals(listOf("a", "b"), store.listAll().map { it.id })
    }

    private fun samplePending(id: String, enqueuedAtMs: Long = 1L) = PendingGpxCommit(
        id = id,
        enqueuedAtMs = enqueuedAtMs,
        request = GpxCommitRequest(
            tracks = listOf(
                GpxTrack(
                    points = listOf(GpxPoint(1.0, 2.0), GpxPoint(1.1, 2.1)),
                    lengthM = 100,
                    durationMin = 5,
                    start = GpxEndpoint.existing(1),
                    end = GpxEndpoint.existing(2),
                ),
            ),
        ),
    )
}
