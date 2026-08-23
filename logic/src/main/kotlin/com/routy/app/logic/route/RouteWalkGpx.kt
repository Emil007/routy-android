package com.routy.app.logic.route

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Minimal GPX 1.1 export for a route-walk track. */
fun buildRouteWalkGpx(points: List<RouteWalkTrackPoint>, trackName: String = "Routy route walk"): String {
    val sb = StringBuilder()
    sb.append("""<?xml version="1.0" encoding="UTF-8"?>""")
    sb.append('\n')
    sb.append("""<gpx version="1.1" creator="Routy" xmlns="http://www.topografix.com/GPX/1/1">""")
    sb.append('\n')
    sb.append("  <trk><name>").append(escapeXml(trackName)).append("</name><trkseg>")
    sb.append('\n')
    for (p in points) {
        sb.append("    <trkpt lat=\"").append(p.lat).append("\" lon=\"").append(p.lng).append("\">")
        p.ele?.let { sb.append("<ele>").append(it).append("</ele>") }
        p.time?.let { sb.append("<time>").append(it).append("</time>") }
        if (p.accuracy != null || p.speed != null || p.bearing != null) {
            sb.append("<extensions><routy:fix")
            sb.append(" xmlns:routy=\"https://routy.app/ns/gpx\"")
            p.accuracy?.let { sb.append(" accuracy=\"").append(it).append('"') }
            p.speed?.let { sb.append(" speed=\"").append(it).append('"') }
            p.bearing?.let { sb.append(" bearing=\"").append(it).append('"') }
            sb.append("/></extensions>")
        }
        sb.append("</trkpt>\n")
    }
    sb.append("  </trkseg></trk>\n</gpx>\n")
    return sb.toString()
}

private fun escapeXml(text: String): String = text
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")

fun gpxFileName(prefix: String = "routy-route"): String {
    val stamp = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
    return "$prefix-$stamp.gpx"
}
