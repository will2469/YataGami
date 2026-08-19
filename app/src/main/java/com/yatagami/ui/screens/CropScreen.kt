package com.yatagami.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.navigation.NavController
import com.yatagami.ui.components.crop.CropBottomBar
import com.yatagami.ui.components.crop.CropGeometryUtils
import com.yatagami.ui.components.crop.CropQuadOverlay
import com.yatagami.ui.components.crop.MagnifierLoupe
import com.yatagami.ui.viewmodel.ScanViewModel
import kotlin.math.min

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
        CropGeometryUtils.isQuadConvexAndNonIntersecting(screenCorners)
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
            CropBottomBar(
                isValidQuad = isValidQuad,
                onResetFull = {
                    screenCorners = listOf(
                        Offset(offsetX, offsetY),
                        Offset(offsetX + bitmap.width * scale, offsetY),
                        Offset(offsetX + bitmap.width * scale, offsetY + bitmap.height * scale),
                        Offset(offsetX, offsetY + bitmap.height * scale)
                    )
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                },
                onResetAuto = {
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
                onApply = {
                    if (isValidQuad && screenCorners.size == 4) {
                        val bitmapCorners = CropGeometryUtils.mapScreenCornersToBitmap(
                            screenCorners, offsetX, offsetY, scale, bitmap.width, bitmap.height
                        )
                        viewModel.updateCropCorners(pageId, bitmapCorners)
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        navController.navigate("filter/$pageId") {
                            popUpTo("crop/$pageId") { inclusive = true }
                        }
                    }
                }
            )
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
            // 1. Document Image View
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Document Capture",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )

            // 2. Interactive Crop Quad Canvas with Magnetic Snap
            CropQuadOverlay(
                screenCorners = screenCorners,
                onCornersChange = { screenCorners = it },
                activeDraggingIndex = activeDraggingIndex,
                onDraggingIndexChange = { activeDraggingIndex = it },
                isValidQuad = isValidQuad,
                bitmap = bitmap,
                scale = scale,
                offsetX = offsetX,
                offsetY = offsetY,
                view = view
            )

            // 3. 2.0x Magnifier Loupe Bubble
            activeDraggingIndex?.let { dragIdx ->
                if (screenCorners.size == 4) {
                    MagnifierLoupe(
                        activePt = screenCorners[dragIdx],
                        bitmap = bitmap,
                        offsetX = offsetX,
                        offsetY = offsetY,
                        scale = scale
                    )
                }
            }
        }
    }
}
