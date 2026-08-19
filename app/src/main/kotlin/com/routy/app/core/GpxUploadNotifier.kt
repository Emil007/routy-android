package com.routy.app.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Background GPX queue outcomes — observed by shell UI for snackbars + Stats refresh. */
object GpxUploadNotifier {
    private val _successes = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val successes: SharedFlow<Unit> = _successes.asSharedFlow()

    private val _failures = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val failures: SharedFlow<Int> = _failures.asSharedFlow()

    fun notifyUploadSucceeded() {
        StatsInvalidation.bump()
        _successes.tryEmit(Unit)
    }

    fun notifyUploadFailed(httpCode: Int) {
        _failures.tryEmit(httpCode)
    }
}
