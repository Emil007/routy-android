package com.routy.app.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Background GPX queue success — observed by shell UI for snackbar + Stats refresh. */
object GpxUploadNotifier {
    private val _successes = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val successes: SharedFlow<Unit> = _successes.asSharedFlow()

    fun notifyUploadSucceeded() {
        StatsInvalidation.bump()
        _successes.tryEmit(Unit)
    }
}
