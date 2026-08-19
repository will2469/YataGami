package com.yatagami.ui.screens

import android.graphics.Bitmap
import android.graphics.PointF
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.yatagami.ui.viewmodel.ScanViewModel
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropScreen(
    pageId: String,
    navController: NavController,
    viewModel: ScanViewModel
) {
    val page = viewModel.pages.find { it.id == pageId }
    if (page == null) {
        LaunchedEffect(Unit) { navController.popBackStack() }
        return
    }

    val view = LocalView.current
    val bitmap = page.originalBitmap
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    // Screen-space 4 corners: TL, TR, BR, BL
    var screenCorners by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var activeDraggingIndex by remember { mutableStateOf<Int?>(null) }
    var isInitialized by remember { mutableStateOf(false) }

    // Coordinate mapping parameters (FIT_CENTER)
    val scale = remember(containerSize, bitmap) {
        if (containerSize.width == 0 || containerSize.height == 0) 1f
        else min(containerSize.width.toFloat() / bitmap.width, containerSize.height.toFloat() / bitmap.height)
    }
    val offsetX = remember(containerSize, bitmap, scale) {
        (containerSize.width.toFloat() - bitmap.width * scale) / 2f
    }
    val offsetY = remember(containerSize, bitmap, scale) {
        (containerSize.height.toFloat() - bitmap.height * scale) / 2f
    }

    // Initialize corners from page model
    LaunchedEffect(containerSize, page) {
        if (containerSize.width > 0 && containerSize.height > 0 && !isInitialized) {
            val initial = if (page.corners.size >= 8) {
                listOf(
                    Offset(page.corners[0] * scale + offsetX, page.corners[1] * scale + offsetY),
                    Offset(page.corners[2] * scale + offsetX, page.corners[3] * scale + offsetY),
                    Offset(page.corners[4] * scale + offsetX, page.corners[5] * scale + offsetY),
                    Offset(page.corners[6] * scale + offsetX, page.corners[7] * scale + offsetY)
                )
            } else {
                listOf(
                    Offset(offsetX, offsetY),
                    Offset(offsetX + bitmap.width * scale, offsetY),
                    Offset(offsetX + bitmap.width * scale, offsetY + bitmap.height * scale),
                    Offset(offsetX, offsetY + bitmap.height * scale)
                )
            }
            screenCorners = initial
            isInitialized = true
        }
    }

    // Real-time quad validation
    val isValidQuad = remember(screenCorners) {
        if (screenCorners.size != 4) false
        else isQuadConvexAndNonIntersecting(screenCorners)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Sesuaikan Sudut Dokumen",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF111827)
                )
            )
        },
        bottomBar = {
            Surface(
                color = Color(0xFF111827),
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    if (!isValidQuad) {
                        Text(
                            text = "⚠️ Sudut bersilangan atau tidak valid!",
                            color = Color(0xFFEF4444),
                            fontSize = 12.sp,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(bottom = 8.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                val full = listOf(
                                    Offset(offsetX, offsetY),
                                    Offset(offsetX + bitmap.width * scale, offsetY),
                                    Offset(offsetX + bitmap.width * scale, offsetY + bitmap.height * scale),
                                    Offset(offsetX, offsetY + bitmap.height * scale)
                                )
                                screenCorners = full
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Full", fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                if (page.originalCorners.size >= 8) {
                                    screenCorners = listOf(
                                        Offset(page.originalCorners[0] * scale + offsetX, page.originalCorners[1] * scale + offsetY),
                                        Offset(page.originalCorners[2] * scale + offsetX, page.originalCorners[3] * scale + offsetY),
                                        Offset(page.originalCorners[4] * scale + offsetX, page.originalCorners[5] * scale + offsetY),
                                        Offset(page.originalCorners[6] * scale + offsetX, page.originalCorners[7] * scale + offsetY)
                                    )
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Auto", fontSize = 13.sp)
                        }

                        Button(
                            onClick = {
                                if (isValidQuad && screenCorners.size == 4) {
                                    val bitmapCorners = FloatArray(8)
                                    for (i in 0 until 4) {
                                        bitmapCorners[i * 2] = ((screenCorners[i].x - offsetX) / scale).coerceIn(0f, bitmap.width.toFloat())
                                        bitmapCorners[i * 2 + 1] = ((screenCorners[i].y - offsetY) / scale).coerceIn(0f, bitmap.height.toFloat())
                                    }
                                    viewModel.updateCropCorners(pageId, bitmapCorners)
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    navController.navigate("filter/$pageId") {
                                        popUpTo("crop/$pageId") { inclusive = true }
                                    }
                                }
                            },
                            enabled = isValidQuad,
                            modifier = Modifier.weight(1.4f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF10B981),
                                disabledContainerColor = Color(0xFF374151)
                            )
                        ) {
                            Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Terapkan", fontSize = 13.sp)
                        }
                    }
                }
            }
        },
        containerColor = Color(0xFF030712)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .onSizeChanged { containerSize = it },
            contentAlignment = Alignment.Center
        ) {
            // Document Image View
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Document Capture",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )

            // Touch Area and Visual Overlay
            val touchRadiusPx = 48.dp.value * 2f
            val strokeColor = if (isValidQuad) Color(0xFF10B981) else Color(0xFFEF4444)
            val fillColor = if (isValidQuad) Color(0x3310B981) else Color(0x33EF4444)

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(scale, offsetX, offsetY, bitmap) {
                        detectDragGestures(
                            onDragStart = { touchOffset ->
                                if (screenCorners.size == 4) {
                                    val closestIdx = screenCorners.indices.minByOrNull { i ->
                                        (screenCorners[i] - touchOffset).getDistance()
                                    }
                                    if (closestIdx != null && (screenCorners[closestIdx] - touchOffset).getDistance() <= touchRadiusPx) {
                                        activeDraggingIndex = closestIdx
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
                                    screenCorners = updated
                                }
                            },
                            onDragEnd = {
                                activeDraggingIndex = null
                                if (!isQuadConvexAndNonIntersecting(screenCorners)) {
                                    view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                                }
                            },
                            onDragCancel = {
                                activeDraggingIndex = null
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

            // 2x Magnifier Loupe Bubble above active dragging corner
            activeDraggingIndex?.let { dragIdx ->
                if (screenCorners.size == 4) {
                    val activePt = screenCorners[dragIdx]
                    val bmpX = ((activePt.x - offsetX) / scale).coerceIn(0f, bitmap.width.toFloat())
                    val bmpY = ((activePt.y - offsetY) / scale).coerceIn(0f, bitmap.height.toFloat())

                    // Loupe position: 75dp above finger
                    val loupeSizeDp = 100.dp
                    val loupeOffsetY = -80.dp

                    Box(
                        modifier = Modifier
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
                                srcOffset = androidx.compose.ui.unit.IntOffset(srcLeft.toInt(), srcTop.toInt()),
                                srcSize = IntSize((srcRight - srcLeft).toInt().coerceAtLeast(1), (srcBottom - srcTop).toInt().coerceAtLeast(1)),
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
            }
        }
    }
}

// Geometric validation: Convexity and Non-Self-Intersection
private fun isQuadConvexAndNonIntersecting(pts: List<Offset>): Boolean {
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
