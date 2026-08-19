package com.routy.app.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** GPX commit queue counts — pending retries vs permanent failures. */
object GpxQueueNotifier {
    private val _pendingCount = MutableStateFlow(0)
    private val _failedCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()
    val failedCount: StateFlow<Int> = _failedCount.asStateFlow()

    fun setCounts(pending: Int, failed: Int) {
        _pendingCount.value = pending.coerceAtLeast(0)
        _failedCount.value = failed.coerceAtLeast(0)
    }
}
