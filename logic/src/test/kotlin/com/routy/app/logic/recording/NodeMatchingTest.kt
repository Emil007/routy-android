package com.routy.app.logic.recording

import com.routy.app.logic.geo.LatLng
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private data class TestNode(override val id: Int, override val name: String?, override val lat: Double, override val lng: Double) :
    MatchableNode

class NodeMatchingTest {
    private val home = TestNode(1, "Home", 52.0, 13.0)
    private val farAway = TestNode(2, "Far Junction", 52.5, 13.5)

    @Test
    fun `finds a candidate within radius, closest first`() {
        val candidates = findNodeCandidates(listOf(home, farAway), LatLng(52.0001, 13.0), radiusM = 50.0)
        assertEquals(1, candidates.size)
        assertEquals(home.id, candidates[0].id)
    }

    @Test
    fun `excludes nodes outside the radius`() {
        val candidates = findNodeCandidates(listOf(farAway), LatLng(52.0, 13.0), radiusM = 50.0)
        assertTrue(candidates.isEmpty())
    }

    @Test
    fun `initialEndpointDecision picks the closest existing node when in range`() {
        val decision = initialEndpointDecision(LatLng(52.0001, 13.0), listOf(home, farAway), mergeRadiusM = 50.0)
        val existing = assertIs<EndpointDecision.Existing>(decision)
        assertEquals(home.id, existing.nodeId)
    }

    @Test
    fun `initialEndpointDecision falls back to a new junction when nothing is close enough`() {
        val decision = initialEndpointDecision(LatLng(10.0, 10.0), listOf(home, farAway), mergeRadiusM = 50.0)
        assertIs<EndpointDecision.NewJunction>(decision)
    }
}
