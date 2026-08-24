package com.routy.app.logic.route

import com.routy.app.logic.api.GeoPoint
import com.routy.app.logic.api.RouteStation
import com.routy.app.logic.api.SegmentDto
import com.routy.app.logic.geo.LatLng
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VoiceAnnounceRadiusTest {
    private val stations = listOf(
        RouteStation(nodeId = 1, lat = 52.0, lng = 13.0),
        RouteStation(nodeId = 2, lat = 52.0, lng = 13.001), // ~69 m east
    )

    @Test
    fun `caps at 40m and floors at 12m`() {
        assertEquals(40.0, voiceAnnounceRadiusM(0, listOf(stations[0])), 0.01)
        val midLeg = voiceAnnounceRadiusM(0, stations)
        assertTrue(midLeg in 12.0..40.0)
        assertEquals(24.0, midLeg, 1.0)
    }
}

class RouteGeometrySplitTest {
    private val geometry = listOf(
        GeoPoint(52.0, 13.0),
        GeoPoint(52.0005, 13.0),
        GeoPoint(52.001, 13.0),
        GeoPoint(52.0015, 13.0),
    )
    private val stations = listOf(
        RouteStation(1, "A", 52.0, 13.0),
        RouteStation(2, "B", 52.0005, 13.0),
        RouteStation(3, "C", 52.0015, 13.0),
    )

    @Test
    fun `returns full geometry before any completion`() {
        assertEquals(geometry, remainingRouteGeometry(geometry, stations, -1))
    }

    @Test
    fun `trims completed legs from planned route`() {
        val remaining = remainingRouteGeometry(geometry, stations, 0)
        assertTrue(remaining.size >= 2)
        assertTrue(remaining.first().lat >= 52.0005 - 0.0001)
    }

    @Test
    fun `returns empty when route fully completed`() {
        assertTrue(remainingRouteGeometry(geometry, stations, stations.lastIndex).isEmpty())
    }
}

class WaypointAdaptiveRadiusTest {
    private val stations = listOf(
        RouteStation(1, "A", 52.0, 13.0),
        RouteStation(2, "B", 52.0, 13.0003), // ~21 m east — adaptive radius ~12 m floor
        RouteStation(3, "C", 52.001, 13.0),
    )

    @Test
    fun `uses adaptive radius not fixed 50m`() {
        val tracker = WaypointProgressTracker(stations)
        // ~14 m from A — outside 12 m adaptive but inside old 50 m fixed
        assertEquals(null, tracker.onLocationUpdate(LatLng(52.0, 13.0002)))
        assertEquals(0, tracker.onLocationUpdate(LatLng(52.0, 13.0)))
    }
}
class OffPathDetectorTest {
    private val path = listOf(
        LatLng(52.0, 13.0),
        LatLng(52.0, 13.001),
    )

    @Test
    fun `warns once after consecutive off-path samples`() {
        val detector = OffPathDetector(path)
        val offPath = LatLng(52.001, 13.0) // ~111 m north of line
        assertFalse(detector.onLocation(offPath))
        assertFalse(detector.onLocation(offPath))
        assertTrue(detector.onLocation(offPath))
        assertFalse(detector.onLocation(offPath))
    }

    @Test
    fun `resets consecutive count when back on path`() {
        val detector = OffPathDetector(path)
        val offPath = LatLng(52.001, 13.0)
        detector.onLocation(offPath)
        detector.onLocation(offPath)
        detector.onLocation(LatLng(52.0, 13.0005))
        assertFalse(detector.onLocation(offPath))
    }
}

class GoldenHopCueTest {
    private val segments = listOf(
        SegmentDto(id = 10, startNodeId = 1, endNodeId = 2, geometry = listOf(GeoPoint(0.0, 0.0), GeoPoint(1.0, 0.0)), lengthM = 100, reverseOf = 11),
        SegmentDto(id = 11, startNodeId = 2, endNodeId = 1, geometry = listOf(GeoPoint(1.0, 0.0), GeoPoint(0.0, 0.0)), lengthM = 100, reverseOf = 10),
    )

    @Test
    fun `cues golden when finishing hop onto station 1`() {
        val canon = goldenCanonicalForFinishedHop(
            arrivedStationIndex = 1,
            routeSegmentIds = listOf(10),
            todayGoldenCanonicalIds = setOf(10),
            segments = segments,
            alreadyCued = emptySet(),
        )
        assertEquals(10, canon)
    }

    @Test
    fun `no cue for start station or already cued`() {
        assertEquals(
            null,
            goldenCanonicalForFinishedHop(0, listOf(10), setOf(10), segments, emptySet()),
        )
        assertEquals(
            null,
            goldenCanonicalForFinishedHop(1, listOf(10), setOf(10), segments, setOf(10)),
        )
    }
}

class RouteWalkTrackSnapshotSerializationTest {
    @Test
    fun `round-trips track snapshot json`() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val original = RouteWalkTrackSnapshot(
            routeKey = "1-2-3",
            points = listOf(RouteWalkTrackPoint(lat = 52.0, lng = 13.0, accuracy = 4.0)),
            goldenHitSegmentIds = listOf(10),
        )
        val encoded = json.encodeToString(RouteWalkTrackSnapshot.serializer(), original)
        val decoded = json.decodeFromString(RouteWalkTrackSnapshot.serializer(), encoded)
        assertEquals(original, decoded)
    }
}
