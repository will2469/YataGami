package com.yatagami.ui.components.crop

import androidx.compose.ui.geometry.Offset

object CropGeometryUtils {

    fun isQuadConvexAndNonIntersecting(pts: List<Offset>): Boolean {
        if (pts.size != 4) return false

        // Cross product test for all 4 vertices
        var positive = false
        var negative = false
        for (i in 0 until 4) {
            val p1 = pts[i]
            val p2 = pts[(i + 1) % 4]
            val p3 = pts[(i + 2) % 4]
            val cross = (p2.x - p1.x) * (p3.y - p2.y) - (p2.y - p1.y) * (p3.x - p2.x)
            if (cross > 0) positive = true
            if (cross < 0) negative = true
            if (positive && negative) return false
        }

        // Diagonal intersection test (must intersect inside quad)
        return doSegmentsIntersect(pts[0], pts[2], pts[1], pts[3])
    }

    private fun doSegmentsIntersect(p1: Offset, p2: Offset, p3: Offset, p4: Offset): Boolean {
        fun ccw(a: Offset, b: Offset, c: Offset): Boolean {
            return (c.y - a.y) * (b.x - a.x) > (b.y - a.y) * (c.x - a.x)
        }
        return ccw(p1, p3, p4) != ccw(p2, p3, p4) && ccw(p1, p2, p3) != ccw(p1, p2, p4)
    }

    fun mapScreenCornersToBitmap(
        screenCorners: List<Offset>,
        offsetX: Float,
        offsetY: Float,
        scale: Float,
        bitmapWidth: Int,
        bitmapHeight: Int
    ): FloatArray {
        val bitmapCorners = FloatArray(8)
        for (i in 0 until 4) {
            bitmapCorners[i * 2] = ((screenCorners[i].x - offsetX) / scale).coerceIn(0f, bitmapWidth.toFloat())
            bitmapCorners[i * 2 + 1] = ((screenCorners[i].y - offsetY) / scale).coerceIn(0f, bitmapHeight.toFloat())
        }
        return bitmapCorners
    }
}
