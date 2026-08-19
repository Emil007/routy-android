package com.routy.app.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Pending GPX commit queue size — shown in shell when uploads are waiting. */
object GpxQueueNotifier {
    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    fun setPendingCount(count: Int) {
        _pendingCount.value = count.coerceAtLeast(0)
    }
}
