package com.routy.app.logic.api

import kotlinx.serialization.Serializable

@Serializable
data class PathProposalDto(
    val id: Int,
    val segmentId: Int,
    val segmentName: String? = null,
    val lat: Double,
    val lng: Double,
    val createdBy: Int,
    val createdAt: String,
)

@Serializable
data class ProposalsResponse(val proposals: List<PathProposalDto>)

@Serializable
data class ProposalActionRequest(val proposalId: Int, val part1: String = "", val part2: String = "")

@Serializable
data class AcceptProposalResponse(val ok: Boolean = true, val newNodeId: Int? = null)
