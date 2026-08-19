package com.routy.app.core.storage

import android.content.Context
import com.routy.app.logic.recording.RecordingConfirmDraft
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RecordingConfirmStore(context: Context) {
    private val file = File(context.filesDir, "recording/confirm_draft.json").apply {
        parentFile?.mkdirs()
    }
    private val json = Json { ignoreUnknownKeys = true }

    fun save(draft: RecordingConfirmDraft) {
        file.writeText(json.encodeToString(draft))
    }

    fun load(): RecordingConfirmDraft? {
        if (!file.exists()) return null
        return runCatching { json.decodeFromString<RecordingConfirmDraft>(file.readText()) }.getOrNull()
    }

    fun clear() {
        file.delete()
    }
}
