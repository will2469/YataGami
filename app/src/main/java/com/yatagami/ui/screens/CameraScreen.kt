package com.yatagami.ui.screens

import android.graphics.PointF
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.yatagami.data.model.DocumentType
import com.yatagami.ui.components.CameraPermissionHandler
import com.yatagami.ui.components.CameraPreview
import com.yatagami.ui.components.DocumentOverlay
import com.yatagami.ui.components.TorchMode
import com.yatagami.ui.components.camera.CameraBottomControls
import com.yatagami.ui.components.camera.CameraTopControls
import com.yatagami.ui.viewmodel.ScanViewModel

@Composable
fun CameraScreen(navController: NavController, viewModel: ScanViewModel) {
    var detectedCorners by remember { mutableStateOf(listOf<PointF>()) }
    var isDocumentStable by remember { mutableStateOf(false) }
    var detectionConfidence by remember { mutableFloatStateOf(0f) }
    var detectionGlareRatio by remember { mutableFloatStateOf(0f) }
    var countdownProgress by remember { mutableFloatStateOf(0f) }
    var isPhoneLevel by remember { mutableStateOf(true) }

    var manualCaptureTrigger by remember { mutableStateOf<(() -> Unit)?>(null) }
    var autoCaptureEnabled by remember { mutableStateOf(true) }
    var torchMode by remember { mutableStateOf(TorchMode.OFF) }
    var showAlignmentGuide by remember { mutableStateOf(true) }
    var selectedPreset by remember { mutableStateOf<DocumentType?>(null) }

    CameraPermissionHandler {
        Scaffold { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // 1. Camera Viewfinder (3 Decoupled Streams)
                CameraPreview(
                    onImageCaptured = { bitmap ->
                        viewModel.addPage(bitmap)
                    },
                    onDocumentDetected = { corners, stable, conf, glare ->
                        detectedCorners = corners
                        isDocumentStable = stable
                        detectionConfidence = conf
                        detectionGlareRatio = glare
                    },
                    autoCaptureEnabled = autoCaptureEnabled,
                    torchMode = torchMode,
                    onCountdownProgress = { progress ->
                        countdownProgress = progress
                    },
                    onDeviceLevelChange = { level ->
                        isPhoneLevel = level
                    },
                    onCameraReady = { trigger ->
                        manualCaptureTrigger = trigger
                    }
                )

                // 2. Real-time Neon Contour & Guide Overlay with Glare Guard
                DocumentOverlay(
                    corners = detectedCorners,
                    isStable = isDocumentStable,
                    confidence = detectionConfidence,
                    glareRatio = detectionGlareRatio,
                    showAlignmentGuide = showAlignmentGuide,
                    isLevel = isPhoneLevel
                )

                // 3. Top Controls Bar & Quick Presets
                CameraTopControls(
                    torchMode = torchMode,
                    onTorchToggle = {
                        torchMode = when (torchMode) {
                            TorchMode.OFF -> TorchMode.AUTO
                            TorchMode.AUTO -> TorchMode.ON
                            TorchMode.ON -> TorchMode.OFF
                        }
                    },
                    showAlignmentGuide = showAlignmentGuide,
                    onGuideToggle = { showAlignmentGuide = !showAlignmentGuide },
                    isDocumentStable = isDocumentStable,
                    detectionConfidence = detectionConfidence,
                    autoCaptureEnabled = autoCaptureEnabled,
                    onAutoCaptureToggle = { autoCaptureEnabled = !autoCaptureEnabled },
                    selectedPreset = selectedPreset,
                    onPresetSelected = { selectedPreset = it },
                    modifier = Modifier.align(Alignment.TopCenter)
                )

                // 4. Bottom Controls: Shutter + Live Multi-Page Thumbnail
                CameraBottomControls(
                    isDocumentStable = isDocumentStable,
                    detectionConfidence = detectionConfidence,
                    countdownProgress = countdownProgress,
                    pages = viewModel.pages,
                    onShutterClick = { manualCaptureTrigger?.invoke() },
                    onThumbnailClick = {
                        if (viewModel.pages.isNotEmpty()) {
                            navController.navigate("pages")
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}
