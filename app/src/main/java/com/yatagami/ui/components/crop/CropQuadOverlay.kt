package com.yatagami.ui.components.crop

import android.graphics.Bitmap
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

@Composable
fun CropQuadOverlay(
    screenCorners: List<Offset>,
    onCornersChange: (List<Offset>) -> Unit,
    activeDraggingIndex: Int?,
    onDraggingIndexChange: (Int?) -> Unit,
    isValidQuad: Boolean,
    bitmap: Bitmap,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    view: View,
    modifier: Modifier = Modifier
) {
    val touchRadiusPx = 48.dp.value * 2f
    val strokeColor = if (isValidQuad) Color(0xFF10B981) else Color(0xFFEF4444)
    val fillColor = if (isValidQuad) Color(0x3310B981) else Color(0x33EF4444)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(scale, offsetX, offsetY, bitmap, screenCorners) {
                detectDragGestures(
                    onDragStart = { touchOffset ->
                        if (screenCorners.size == 4) {
                            val closestIdx = screenCorners.indices.minByOrNull { i ->
                                (screenCorners[i] - touchOffset).getDistance()
                            }
                            if (closestIdx != null && (screenCorners[closestIdx] - touchOffset).getDistance() <= touchRadiusPx) {
                                onDraggingIndexChange(closestIdx)
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        activeDraggingIndex?.let { idx ->
                            val current = screenCorners[idx]
                            var newX = (current.x + dragAmount.x).coerceIn(offsetX, offsetX + bitmap.width * scale)
                            var newY = (current.y + dragAmount.y).coerceIn(offsetY, offsetY + bitmap.height * scale)

                            // Magnetic Snap to image border (threshold: 14px)
                            val snapThreshold = 14f
                            if (abs(newX - offsetX) < snapThreshold) newX = offsetX
                            if (abs(newX - (offsetX + bitmap.width * scale)) < snapThreshold) newX = offsetX + bitmap.width * scale
                            if (abs(newY - offsetY) < snapThreshold) newY = offsetY
                            if (abs(newY - (offsetY + bitmap.height * scale)) < snapThreshold) newY = offsetY + bitmap.height * scale

                            val updated = screenCorners.toMutableList()
                            updated[idx] = Offset(newX, newY)
                            onCornersChange(updated)
                        }
                    },
                    onDragEnd = {
                        onDraggingIndexChange(null)
                        if (!CropGeometryUtils.isQuadConvexAndNonIntersecting(screenCorners)) {
                            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                        }
                    },
                    onDragCancel = {
                        onDraggingIndexChange(null)
                    }
                )
            }
    ) {
        if (screenCorners.size == 4) {
            val path = Path().apply {
                moveTo(screenCorners[0].x, screenCorners[0].y)
                lineTo(screenCorners[1].x, screenCorners[1].y)
                lineTo(screenCorners[2].x, screenCorners[2].y)
                lineTo(screenCorners[3].x, screenCorners[3].y)
                close()
            }

            // Semi-transparent polygon fill
            drawPath(path, color = fillColor)

            // Bold polygon outline
            drawPath(
                path,
                color = strokeColor,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // Draw corner handles
            for (i in 0 until 4) {
                val pt = screenCorners[i]
                val isDragging = (activeDraggingIndex == i)

                // Outer glow
                drawCircle(
                    color = strokeColor.copy(alpha = if (isDragging) 0.6f else 0.3f),
                    radius = if (isDragging) 22.dp.toPx() else 16.dp.toPx(),
                    center = pt
                )
                // White solid center
                drawCircle(
                    color = Color.White,
                    radius = if (isDragging) 10.dp.toPx() else 7.dp.toPx(),
                    center = pt
                )
                // Accent ring
                drawCircle(
                    color = strokeColor,
                    radius = if (isDragging) 10.dp.toPx() else 7.dp.toPx(),
                    center = pt,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
    }
}
