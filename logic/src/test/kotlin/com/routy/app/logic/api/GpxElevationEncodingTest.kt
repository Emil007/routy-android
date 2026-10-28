package com.routy.app.logic.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class GpxElevationEncodingTest {
    private val json = Json { explicitNulls = false }

    @Test
    fun `GpxElevation with null elevation encodes as explicit JSON null`() {
        val track = GpxTrack(
            points = listOf(GpxPoint(52.0, 13.0), GpxPoint(52.001, 13.0)),
            lengthM = 100,
            durationMin = 2,
            elevation = null,
            start = GpxEndpoint.existing(1),
            end = GpxEndpoint.new("a", "b"),
        )
        val encoded = json.encodeToString(GpxTrack.serializer(), track)
        // M3: elevation must be present as JSON null even with explicitNulls=false.
        assertEquals(true, encoded.contains("\"elevation\":null"), "expected explicit elevation:null, got $encoded")
    }
}
