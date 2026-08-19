package com.routy.app.logic.map

/** Raster base layers — URLs match bundled style JSON assets under app assets styles. */
enum class MapTileStyle {
    HIKING,
    STREETS,
    SATELLITE,
}

fun tileUrl(style: MapTileStyle, z: Int, x: Int, y: Int): String = when (style) {
    MapTileStyle.HIKING -> {
        val sub = listOf("a", "b", "c")[(x + y) % 3]
        "https://$sub.tile.opentopomap.org/$z/$x/$y.png"
    }
    MapTileStyle.STREETS -> "https://tile.openstreetmap.org/$z/$x/$y.png"
    MapTileStyle.SATELLITE -> "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$z/$y/$x"
}
