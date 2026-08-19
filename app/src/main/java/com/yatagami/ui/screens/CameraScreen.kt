package com.yatagami.ui.screens

import android.graphics.PointF
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.yatagami.data.model.DocumentType
import com.yatagami.ui.components.CameraPermissionHandler
import com.yatagami.ui.components.CameraPreview
import com.yatagami.ui.components.DocumentOverlay
import com.yatagami.ui.components.TorchMode

val docPresets = listOf(
    "Auto" to null,
    "A4" to DocumentType.A4,
    "KTP / ID" to DocumentType.KTP,
    "F4 / Folio" to DocumentType.F4,
    "Struk" to DocumentType.RECEIPT,
    "Foto" to DocumentType.SQUARE
)

@Composable
fun CameraScreen(navController: NavController, viewModel: com.yatagami.ui.viewmodel.ScanViewModel) {
    var detectedCorners by remember { mutableStateOf(listOf<PointF>()) }
    var isDocumentStable by remember { mutableStateOf(false) }
    var detectionConfidence by remember { mutableFloatStateOf(0f) }
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
                    onDocumentDetected = { corners, stable, conf ->
                        detectedCorners = corners
                        isDocumentStable = stable
                        detectionConfidence = conf
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

                // 2. Real-time Neon Contour & Guide Overlay
                DocumentOverlay(
                    corners = detectedCorners,
                    isStable = isDocumentStable,
                    confidence = detectionConfidence,
                    showAlignmentGuide = showAlignmentGuide,
                    isLevel = isPhoneLevel
                )

                // 3. Top Controls Bar & Quick Presets
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp, start = 12.dp, end = 12.dp)
                ) {
                    // Top Bar: Torch, Grid Guide, Status Badge, Auto-Shutter
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Torch Toggle Button
                        IconButton(
                            onClick = {
                                torchMode = when (torchMode) {
                                    TorchMode.OFF -> TorchMode.AUTO
                                    TorchMode.AUTO -> TorchMode.ON
                                    TorchMode.ON -> TorchMode.OFF
                                }
                            },
                            modifier = Modifier
                                .background(Color(0x99000000), CircleShape)
                                .size(40.dp)
                        ) {
                            Text(
                                text = when (torchMode) {
                                    TorchMode.OFF -> "🚫🔦"
                                    TorchMode.AUTO -> "⚡🔦"
                                    TorchMode.ON -> "🔦"
                                },
                                fontSize = 16.sp
                            )
                        }

                        // Alignment Guide Toggle
                        IconButton(
                            onClick = { showAlignmentGuide = !showAlignmentGuide },
                            modifier = Modifier
                                .background(
                                    if (showAlignmentGuide) Color(0xCC2E7D32) else Color(0x99000000),
                                    CircleShape
                                )
                                .size(40.dp)
                        ) {
                            Text("📐", fontSize = 16.sp)
                        }

                        // Real-time Detection Status Badge
                        Row(
                            modifier = Modifier
                                .background(
                                    color = if (isDocumentStable && detectionConfidence >= 0.75f) Color(0xCC00E676)
                                    else if (detectionConfidence >= 0.40f) Color(0xCCFFD600)
                                    else Color(0x99000000),
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        color = if (isDocumentStable && detectionConfidence >= 0.75f) Color.White else Color.Black,
                                        shape = CircleShape
                                    )
                            )
                            Text(
                                text = if (isDocumentStable && detectionConfidence >= 0.75f) " Siap"
                                else if (detectionConfidence >= 0.40f) " Menyesuaikan..."
                                else " Arahkan Dokumen",
                                color = if (isDocumentStable && detectionConfidence >= 0.75f) Color.Black else Color.White,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }

                        // Auto Capture Toggle Chip
                        FilterChip(
                            selected = autoCaptureEnabled,
                            onClick = { autoCaptureEnabled = !autoCaptureEnabled },
                            label = { Text(if (autoCaptureEnabled) "Auto ON" else "Auto OFF", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xCC2E7D32),
                                selectedLabelColor = Color.White
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Document Type Preset Quick Selector Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        docPresets.forEach { (label, presetType) ->
                            val isSelected = selectedPreset == presetType
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = if (isSelected) Color(0xFF00E676) else Color(0x99000000),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .clickable { selectedPreset = presetType }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.Black else Color.White,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                // 4. Bottom Controls: Shutter + Mini Page Preview Thumbnail
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 28.dp, start = 24.dp, end = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left spacer for symmetric balance
                    Box(modifier = Modifier.size(56.dp))

                    // Center: Shutter Button with Circular Countdown Progress Ring
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(84.dp)
                    ) {
                        // Countdown Progress Ring (0f..1f)
                        if (countdownProgress > 0f) {
                            CircularProgressIndicator(
                                progress = { countdownProgress },
                                modifier = Modifier.fillMaxSize(),
                                color = Color(0xFF00E676),
                                strokeWidth = 5.dp
                            )
                        }

                        // Inner Shutter Button
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .border(3.5.dp, Color.White, CircleShape)
                                .padding(5.dp)
                                .background(
                                    color = if (isDocumentStable && detectionConfidence >= 0.75f) Color(0xFF00E676) else Color.White,
                                    shape = CircleShape
                                )
                                .clickable {
                                    manualCaptureTrigger?.invoke()
                                }
                        )
                    }

                    // Right: Mini Thumbnail with Multi-Page Batch Count Badge
                    BadgedBox(
                        badge = {
                            if (viewModel.pages.isNotEmpty()) {
                                Badge(
                                    containerColor = Color(0xFF00E676),
                                    contentColor = Color.Black
                                ) {
                                    Text("${viewModel.pages.size}")
                                }
                            }
                        }
                    ) {
                        val lastPage = viewModel.pages.lastOrNull()
                        val thumbnailBitmap = lastPage?.getDisplayBitmap()

                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x99000000))
                                .border(2.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                .clickable {
                                    if (viewModel.pages.isNotEmpty()) {
                                        navController.navigate("pages")
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (thumbnailBitmap != null && !thumbnailBitmap.isRecycled) {
                                Image(
                                    bitmap = thumbnailBitmap.asImageBitmap(),
                                    contentDescription = "Preview Halaman",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Halaman",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
