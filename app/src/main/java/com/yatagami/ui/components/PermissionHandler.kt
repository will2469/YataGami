package com.yatagami.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraPermissionHandler(content: @Composable () -> Unit) {
    val cameraPermission = rememberPermissionState(android.Manifest.permission.CAMERA)

    when {
        cameraPermission.status.isGranted -> content()
        cameraPermission.status.shouldShowRationale -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Izin Kamera Diperlukan") },
                text = { Text("Aplikasi ini butuh akses kamera buat mindai dokumen.") },
                confirmButton = {
                    TextButton(onClick = { cameraPermission.launchPermissionRequest() }) {
                        Text("Izinkan")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { /* exit */ }) { Text("Tutup") }
                }
            )
        }
        else -> {
            cameraPermission.launchPermissionRequest()
        }
    }
}
