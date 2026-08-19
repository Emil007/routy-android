package com.routy.app.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.routy.app.logic.api.GeoPoint
import kotlin.math.max

@Composable
fun WalkPathThumbnail(
    points: List<GeoPoint>,
    modifier: Modifier = Modifier,
) {
    if (points.size < 2) return

    val lats = points.map { it.lat }
    val lngs = points.map { it.lng }
    val minLat = lats.min()
    val maxLat = lats.max()
    val minLng = lngs.min()
    val maxLng = lngs.max()
    val pad = 0.00001
    val latSpan = max(maxLat - minLat, pad)
    val lngSpan = max(maxLng - minLng, pad)

    Canvas(modifier = modifier.size(width = 48.dp, height = 44.dp)) {
        val path = Path()
        points.forEachIndexed { index, point ->
            val x = ((point.lng - minLng) / lngSpan).toFloat() * size.width * 0.88f + size.width * 0.06f
            val y = size.height * 0.92f - ((point.lat - minLat) / latSpan).toFloat() * size.height * 0.8f
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = androidx.compose.ui.graphics.Color(0xFF2E6B49),
            style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
