package com.routy.app.logic.api

import kotlinx.serialization.Serializable

@Serializable
data class CrashReportRequest(
    val message: String,
    val stack: String? = null,
    val appVersion: String? = null,
)
