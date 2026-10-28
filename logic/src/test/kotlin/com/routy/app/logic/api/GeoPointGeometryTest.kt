package com.routy.app.logic.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json

class GeoPointGeometryTest {
    @Test
    fun `decodes tuple geometry from legacy cache`() {
        val json = """{"nodeChain":[1,2],"segmentIds":[1],"lengthM":100,"durationMin":2,"stations":[],"shortStationGroups":[],"geometry":[[48.0,16.0],[48.1,16.1]]}"""
        val payload = Json { ignoreUnknownKeys = true }.decodeFromString<RouteDisplayPayload>(json)
        assertEquals(2, payload.geometry.size)
        assertEquals(GeoPoint(48.0, 16.0), payload.geometry[0])
        assertEquals(GeoPoint(48.1, 16.1), payload.geometry[1])
    }

    @Test
    fun `decodes object geometry`() {
        val json = """{"nodeChain":[1],"segmentIds":[1],"lengthM":10,"durationMin":1,"stations":[],"shortStationGroups":[],"geometry":[{"lat":48.0,"lng":16.0}]}"""
        val payload = Json { ignoreUnknownKeys = true }.decodeFromString<RouteDisplayPayload>(json)
        assertEquals(1, payload.geometry.size)
        assertEquals(GeoPoint(48.0, 16.0), payload.geometry[0])
    }

    @Test
    fun `null geometry decodes to empty list`() {
        val json = """{"nodeChain":[1],"segmentIds":[1],"lengthM":10,"durationMin":1,"stations":[],"shortStationGroups":[],"geometry":null}"""
        val payload = Json { ignoreUnknownKeys = true }.decodeFromString<RouteDisplayPayload>(json)
        assertEquals(emptyList(), payload.geometry)
    }
}
