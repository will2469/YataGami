package com.yatagami.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.yatagami.opencv.DocumentDetector
import com.yatagami.ui.components.camera.CameraFeedbackHelper
import com.yatagami.ui.components.camera.CameraStreamManager
import com.yatagami.ui.components.camera.FrameAnalyzerHelper
import java.util.concurrent.Executors
import kotlin.math.abs

enum class TorchMode {
    OFF,
    AUTO,
    ON
}

sealed class AutoCaptureState {
    object Idle : AutoCaptureState()
    object Stabilizing : AutoCaptureState()
    data class CountingDown(val progress: Float) : AutoCaptureState()
    object Capturing : AutoCaptureState()
}

@Composable
fun CameraPreview(
    onImageCaptured: (Bitmap) -> Unit,
    onDocumentDetected: (List<PointF>, Boolean, Float, Float) -> Unit,
    autoCaptureEnabled: Boolean = true,
    torchMode: TorchMode = TorchMode.OFF,
    onCountdownProgress: (Float) -> Unit = {},
    onDeviceLevelChange: (Boolean) -> Unit = {},
    onCameraReady: ((() -> Unit) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val detector = remember { DocumentDetector() }
    val frameAnalyzerHelper = remember { FrameAnalyzerHelper(detector) }

    var isCapturingFlash by remember { mutableStateOf(false) }
    var currentCamera by remember { mutableStateOf<Camera?>(null) }
    var isPhoneLevel by remember { mutableStateOf(true) }

    // Accelerometer / Level Monitor
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                // Device is roughly flat / parallel to document if gravity is mostly along Z axis
                val isFlat = abs(z) > 8.0f && abs(x) < 2.5f && abs(y) < 2.5f
                if (isFlat != isPhoneLevel) {
                    isPhoneLevel = isFlat
                    onDeviceLevelChange(isFlat)
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorManager?.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        onDispose {
            sensorManager?.unregisterListener(listener)
            executor.shutdown()
        }
    }

    // Torch state handler
    LaunchedEffect(torchMode, currentCamera) {
        currentCamera?.cameraControl?.enableTorch(torchMode == TorchMode.ON)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val provider = cameraProviderFuture.get()

                    // Stream 1: 30 FPS 720p Preview
                    val preview = CameraStreamManager.buildPreview(previewView)

                    // Stream 3: 12MP 4000x3000 ImageCapture
                    val imageCapture = CameraStreamManager.buildImageCapture()

                    // Stream 2: 10-15 FPS 720p ImageAnalysis
                    val imageAnalysis = CameraStreamManager.buildImageAnalysis().also {
                        it.setAnalyzer(executor) { imageProxy ->
                            frameAnalyzerHelper.analyze(
                                imageProxy = imageProxy,
                                onDocumentDetected = onDocumentDetected,
                                autoCaptureEnabled = autoCaptureEnabled,
                                onCountdownProgress = onCountdownProgress,
                                onTriggerCapture = {
                                    CameraFeedbackHelper.triggerCaptureFeedback(context) { isCapturingFlash = it }
                                    CameraFeedbackHelper.takePictureDirect(imageCapture, executor, onImageCaptured)
                                }
                            )
                        }
                    }

                    try {
                        provider.unbindAll()
                        val camera: Camera = provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageCapture,
                            imageAnalysis
                        )
                        currentCamera = camera

                        // Expose manual shutter trigger
                        onCameraReady?.invoke {
                            CameraFeedbackHelper.triggerCaptureFeedback(context) { isCapturingFlash = it }
                            CameraFeedbackHelper.takePictureDirect(imageCapture, executor, onImageCaptured)
                        }
                    } catch (e: Exception) {
                        Log.e("CameraPreview", "Camera bind failed", e)
                    }
                }, ContextCompat.getMainExecutor(context))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Subtle Shutter Flash Overlay (35% opacity, 50ms)
        AnimatedVisibility(
            visible = isCapturingFlash,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.35f))
            )
        }
    }
}
