package com.routy.app.logic.route

import com.routy.app.logic.api.FreeRoamNeighbour
import com.routy.app.logic.api.FreeRoamReachedNode

/**
 * M8: Localized "via" word for the free-roam neighbour fallback (the app ships de + en).
 * Server announcements are fully localized; this only backs the case where the server
 * text is null/blank.
 */
fun viaWordForLocale(localeTag: String?): String =
    if (localeTag?.trim()?.startsWith("en", ignoreCase = true) == true) "via" else "über"

/** One neighbour rendered as "Name über Via" (localized), with "#id" / plain-name fallbacks. */
fun neighbourLine(nb: FreeRoamNeighbour, viaWord: String): String {
    val name = nb.name?.takeIf { it.isNotBlank() } ?: "#${nb.nodeId}"
    val via = nb.viaSegmentName?.takeIf { it.isNotBlank() }
    return if (via != null) "$name $viaWord $via" else name
}

/**
 * M8: Free-roam announce fallback — reached node name plus its reachable neighbours.
 * The reached name is always part of the line, so two arrivals with the same neighbour
 * line produce different texts (and re-trigger TTS after the previous one was consumed).
 */
fun fallbackLine(reachedName: String, neighbours: List<FreeRoamNeighbour>, viaWord: String): String {
    val head = reachedName.trim()
    val lines = neighbours.map { neighbourLine(it, viaWord) }.filter { it.isNotBlank() }
    return (listOf(head).filter { it.isNotBlank() } + lines).joinToString(", ")
}

/** Joins multiple newly-reached nodes into one fallback announce line. */
fun fallbackLines(nodes: List<FreeRoamReachedNode>, viaWord: String): String =
    nodes.joinToString(", ") { reached ->
        val name = reached.name?.takeIf { it.isNotBlank() } ?: "#${reached.nodeId}"
        fallbackLine(name, reached.reachable, viaWord)
    }
