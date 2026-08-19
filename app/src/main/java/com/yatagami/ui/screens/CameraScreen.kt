package com.yatagami.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.yatagami.ui.components.CameraPermissionHandler
import com.yatagami.ui.components.CameraPreview
import com.yatagami.ui.components.DocumentOverlay

@Composable
fun CameraScreen(navController: NavController, viewModel: com.yatagami.ui.viewmodel.ScanViewModel) {
    var detectedCorners by remember { mutableStateOf(listOf<android.graphics.PointF>()) }

    CameraPermissionHandler {
        Scaffold(
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { navController.navigate("pages") },
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Lihat Halaman")
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                CameraPreview(
                    onImageCaptured = { bitmap ->
                        viewModel.addPage(bitmap)
                    },
                    onDocumentDetected = { corners ->
                        detectedCorners = corners
                    },
                    autoCaptureEnabled = true
                )
                DocumentOverlay(detectedCorners)

                FilterChip(
                    selected = viewModel.isAutoSaveJpg.value,
                    onClick = { viewModel.isAutoSaveJpg.value = !viewModel.isAutoSaveJpg.value },
                    label = {
                        Text(if (viewModel.isAutoSaveJpg.value) "Auto Save JPG: ON" else "Auto Save JPG: OFF")
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                )
            }
        }
    }
}
