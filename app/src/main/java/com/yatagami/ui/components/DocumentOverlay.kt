package com.yatagami.ui.components

import android.graphics.PointF
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yatagami.R
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun DocumentOverlay(
    corners: List<PointF>,
    isStable: Boolean = false,
    confidence: Float = 0f,
    glareRatio: Float = 0f,
    showAlignmentGuide: Boolean = true,
    isLevel: Boolean = true,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height

            // 1. Document Frame Alignment Guide (85% of screen area)
            if (showAlignmentGuide) {
                val frameW = canvasW * 0.86f
                val frameH = canvasH * 0.72f
                val left = (canvasW - frameW) / 2f
                val top = (canvasH - frameH) / 2f
                val framePath = Path().apply {
                    addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(
                            left = left,
                            top = top,
                            right = left + frameW,
                            bottom = top + frameH,
                            radiusX = 16.dp.toPx(),
                            radiusY = 16.dp.toPx()
                        )
                    )
                }
                drawPath(
                    framePath,
                    color = Color.White.copy(alpha = 0.20f),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }

            // 2. Real-time Neon Document Contour Polygon
            if (corners.size == 4 && confidence >= 0.40f) {
                val pts = corners.map { Offset(it.x * canvasW, it.y * canvasH) }
                val mainColor = if (isStable && confidence >= 0.75f) {
                    Color(0xFF00E676) // Neon Green
                } else {
                    Color(0xFFFFD600) // Amber Yellow
                }

                val polyPath = Path().apply {
                    moveTo(pts[0].x, pts[0].y)
                    lineTo(pts[1].x, pts[1].y)
                    lineTo(pts[2].x, pts[2].y)
                    lineTo(pts[3].x, pts[3].y)
                    close()
                }

                // Semi-transparent interior fill
                drawPath(
                    polyPath,
                    color = mainColor.copy(alpha = if (isStable) 0.15f else 0.08f)
                )

                // Outer border stroke
                drawPath(
                    polyPath,
                    color = mainColor,
                    style = Stroke(width = if (isStable) 3.dp.toPx() else 2.dp.toPx())
                )

                // 3. Edge-Oriented L-Bracket Corner Accents
                val bracketLen = 22.dp.toPx()
                for (i in 0 until 4) {
                    val pCurr = pts[i]
                    val pPrev = pts[(i + 3) % 4]
                    val pNext = pts[(i + 1) % 4]

                    val anglePrev = atan2((pPrev.y - pCurr.y).toDouble(), (pPrev.x - pCurr.x).toDouble()).toFloat()
                    val angleNext = atan2((pNext.y - pCurr.y).toDouble(), (pNext.x - pCurr.x).toDouble()).toFloat()

                    val armPrev = Offset(
                        pCurr.x + bracketLen * cos(anglePrev),
                        pCurr.y + bracketLen * sin(anglePrev)
                    )
                    val armNext = Offset(
                        pCurr.x + bracketLen * cos(angleNext),
                        pCurr.y + bracketLen * sin(angleNext)
                    )

                    drawLine(
                        color = Color.White,
                        start = pCurr,
                        end = armPrev,
                        strokeWidth = 4.dp.toPx()
                    )
                    drawLine(
                        color = Color.White,
                        start = pCurr,
                        end = armNext,
                        strokeWidth = 4.dp.toPx()
                    )
                    drawCircle(
                        color = mainColor,
                        radius = 4.dp.toPx(),
                        center = pCurr
                    )
                }
            }
        }

        // 4. Orientation Level Indicator
        if (showAlignmentGuide) {
            Icon(
                imageVector = if (isLevel) Icons.Default.Check else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isLevel) Color(0xFF00E676) else Color(0xFFFFD600),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 72.dp)
            )
        }

        // 5. Specular Glare Warning Banner (Animated Warning Pill)
        AnimatedVisibility(
            visible = glareRatio > 0.05f,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 104.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(Color(0xCC1E1E1E), shape = RoundedCornerShape(20.dp))
                    .border(1.dp, Color(0xFFFFB300), RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFFFB300),
                    modifier = Modifier.padding(end = 6.dp)
                )
                Text(
                    text = stringResource(R.string.warning_glare_detected),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
