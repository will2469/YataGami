package com.yatagami.ui.components.crop

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

@Composable
fun MagnifierLoupe(
    activePt: Offset,
    bitmap: Bitmap,
    offsetX: Float,
    offsetY: Float,
    scale: Float,
    modifier: Modifier = Modifier
) {
    val bmpX = ((activePt.x - offsetX) / scale).coerceIn(0f, bitmap.width.toFloat())
    val bmpY = ((activePt.y - offsetY) / scale).coerceIn(0f, bitmap.height.toFloat())

    val loupeSizeDp = 100.dp
    val loupeOffsetY = -80.dp

    Box(
        modifier = modifier
            .offset {
                IntOffset(
                    x = (activePt.x - (loupeSizeDp.toPx() / 2)).toInt(),
                    y = (activePt.y + loupeOffsetY.toPx()).toInt()
                )
            }
            .size(loupeSizeDp)
            .shadow(12.dp, CircleShape)
            .clip(CircleShape)
            .border(3.dp, Color(0xFF10B981), CircleShape)
            .background(Color.Black)
    ) {
        // Magnified Bitmap View (2.0x Zoom)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val zoomFactor = 2.0f
            val cropRadius = (size.width / (2f * zoomFactor * scale))
            val srcLeft = (bmpX - cropRadius).coerceIn(0f, bitmap.width.toFloat())
            val srcTop = (bmpY - cropRadius).coerceIn(0f, bitmap.height.toFloat())
            val srcRight = (bmpX + cropRadius).coerceIn(0f, bitmap.width.toFloat())
            val srcBottom = (bmpY + cropRadius).coerceIn(0f, bitmap.height.toFloat())

            drawImage(
                image = bitmap.asImageBitmap(),
                srcOffset = IntOffset(srcLeft.toInt(), srcTop.toInt()),
                srcSize = IntSize(
                    (srcRight - srcLeft).toInt().coerceAtLeast(1),
                    (srcBottom - srcTop).toInt().coerceAtLeast(1)
                ),
                dstSize = IntSize(size.width.toInt(), size.height.toInt())
            )

            // Loupe Crosshair
            val midX = size.width / 2f
            val midY = size.height / 2f
            drawLine(
                color = Color(0xFF10B981),
                start = Offset(midX - 15f, midY),
                end = Offset(midX + 15f, midY),
                strokeWidth = 2.dp.toPx()
            )
            drawLine(
                color = Color(0xFF10B981),
                start = Offset(midX, midY - 15f),
                end = Offset(midX, midY + 15f),
                strokeWidth = 2.dp.toPx()
            )
            drawCircle(
                color = Color.White,
                radius = 3.dp.toPx(),
                center = Offset(midX, midY)
            )
        }
    }
}
