package com.yatagami.ui.screens

import android.graphics.PointF
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.yatagami.ui.components.CameraPermissionHandler
import com.yatagami.ui.components.CameraPreview
import com.yatagami.ui.components.DocumentOverlay

@Composable
fun CameraScreen(navController: NavController, viewModel: com.yatagami.ui.viewmodel.ScanViewModel) {
    var detectedCorners by remember { mutableStateOf(listOf<PointF>()) }
    var isDocumentStable by remember { mutableStateOf(false) }
    var manualCaptureTrigger by remember { mutableStateOf<(() -> Unit)?>(null) }
    var autoCaptureEnabled by remember { mutableStateOf(true) }

    CameraPermissionHandler {
        Scaffold { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Camera Viewfinder
                CameraPreview(
                    onImageCaptured = { bitmap ->
                        viewModel.addPage(bitmap)
                    },
                    onDocumentDetected = { corners, stable ->
                        detectedCorners = corners
                        isDocumentStable = stable
                    },
                    autoCaptureEnabled = autoCaptureEnabled,
                    onCameraReady = { trigger ->
                        manualCaptureTrigger = trigger
                    }
                )

                // Real-time Document Contour Overlay
                DocumentOverlay(detectedCorners)

                // Top Controls & Status Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Status Badge
                    Row(
                        modifier = Modifier
                            .background(
                                color = if (isDocumentStable) Color(0xCC2E7D32) else Color(0xCC000000),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = if (isDocumentStable) Color.Green else Color.Yellow,
                                    shape = CircleShape
                                )
                        )
                        Text(
                            text = if (isDocumentStable) " Dokumen Siap" else " Arahkan Dokumen",
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }

                    // Auto Capture Toggle Chip
                    FilterChip(
                        selected = autoCaptureEnabled,
                        onClick = { autoCaptureEnabled = !autoCaptureEnabled },
                        label = {
                            Text(if (autoCaptureEnabled) "Auto Shutter: ON" else "Auto Shutter: OFF")
                        }
                    )
                }

                // Bottom Action Bar: Manual Shutter + Pages Gallery FAB
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp, start = 24.dp, end = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Placeholder balance spacing
                    Box(modifier = Modifier.size(56.dp))

                    // Manual Shutter Button
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .border(4.dp, Color.White, CircleShape)
                            .padding(6.dp)
                            .background(
                                color = if (isDocumentStable) Color(0xFF4CAF50) else Color.White,
                                shape = CircleShape
                            )
                            .clickable {
                                manualCaptureTrigger?.invoke()
                            }
                    )

                    // Page List Button with Counter Badge
                    BadgedBox(
                        badge = {
                            if (viewModel.pages.isNotEmpty()) {
                                Badge { Text("${viewModel.pages.size}") }
                            }
                        }
                    ) {
                        FloatingActionButton(
                            onClick = { navController.navigate("pages") },
                            containerColor = Color.White,
                            contentColor = Color.Black
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Lihat Halaman")
                        }
                    }
                }
            }
        }
    }
}
