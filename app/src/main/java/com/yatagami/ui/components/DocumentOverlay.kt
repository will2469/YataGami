package com.yatagami.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun DocumentOverlay(corners: List<android.graphics.PointF>) {
    if (corners.size != 4) return
    Canvas(modifier = Modifier.fillMaxSize()) {
        val pts = corners.map { Offset(it.x * size.width, it.y * size.height) }
        val path = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            lineTo(pts[1].x, pts[1].y)
            lineTo(pts[2].x, pts[2].y)
            lineTo(pts[3].x, pts[3].y)
            close()
        }
        drawPath(path, color = Color.Transparent)
        drawPath(path, color = Color.Green, style = Stroke(width = 3.dp.toPx()))

        pts.forEach {
            drawCircle(Color.Red, radius = 12.dp.toPx(), center = it)
        }
    }
}
