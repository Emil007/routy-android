package com.routy.app.logic.route

import com.routy.app.logic.geo.CompassPoint

/**
 * M27: maps the server's `directionCompass` field ("N", "NE", …) to the CompassPoint enum
 * so the UI can render a localized direction label next to each reachable neighbour (parity
 * with the web neighbour list). Returns null for unknown/blank values.
 */
fun compassPointFromAbbreviation(abbrev: String?): CompassPoint? {
    if (abbrev.isNullOrBlank()) return null
    return when (abbrev.trim().uppercase()) {
        "N" -> CompassPoint.N
        "NE" -> CompassPoint.NE
        "E" -> CompassPoint.E
        "SE" -> CompassPoint.SE
        "S" -> CompassPoint.S
        "SW" -> CompassPoint.SW
        "W" -> CompassPoint.W
        "NW" -> CompassPoint.NW
        else -> null
    }
}
