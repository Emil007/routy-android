package com.routy.app.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Bumped after route completion so the Stats tab can refresh without manual retry. */
object StatsInvalidation {
    private val _version = MutableStateFlow(0)
    val version = _version.asStateFlow()

    fun bump() {
        _version.value++
    }
}
