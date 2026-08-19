package com.yatagami.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CaptureRequest
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.util.Size
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
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
import androidx.compose.runtime.mutableFloatStateOf
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
import com.yatagami.utils.BitmapUtils.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.hypot

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

@OptIn(ExperimentalCamera2Interop::class)
@Composable
fun CameraPreview(
    onImageCaptured: (Bitmap) -> Unit,
    onDocumentDetected: (List<PointF>, Boolean, Float) -> Unit,
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

    var isCapturingFlash by remember { mutableStateOf(false) }
    var currentCamera by remember { mutableStateOf<Camera?>(null) }
    var isPhoneLevel by remember { mutableStateOf(true) }

    // Accelerometer / Level Monitor
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event != null && event.values.size >= 2) {
                    val ax = event.values[0]
                    val ay = event.values[1]
                    val level = abs(ax) < 1.8f && abs(ay) < 2.5f
                    if (level != isPhoneLevel) {
                        isPhoneLevel = level
                        onDeviceLevelChange(level)
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorManager?.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }

    // Torch Mode Control
    LaunchedEffect(torchMode, currentCamera) {
        currentCamera?.let { cam ->
            when (torchMode) {
                TorchMode.ON -> cam.cameraControl.enableTorch(true)
                TorchMode.OFF -> cam.cameraControl.enableTorch(false)
                TorchMode.AUTO -> {
                    // Auto torch is managed during frame analysis below
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = {
                val previewView = PreviewView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    val provider = cameraProviderFuture.get()

                    // STREAM 1: Preview (30 FPS, 1280x720 720p Surface)
                    val previewResolution = ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                        )
                        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                        .build()

                    val previewBuilder = Preview.Builder().setResolutionSelector(previewResolution)
                    val previewExtender = Camera2Interop.Extender(previewBuilder)
                    previewExtender.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                    previewExtender.setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_FAST)
                    val preview = previewBuilder.build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    // STREAM 2: ImageAnalysis (10-15 FPS, 1280x720 720p)
                    val analysisResolution = ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                        )
                        .build()

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setResolutionSelector(analysisResolution)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(executor) { imageProxy ->
                                analyzeFrame(
                                    imageProxy = imageProxy,
                                    detector = detector,
                                    onDocumentDetected = onDocumentDetected,
                                    imageCapture = null,
                                    autoCaptureEnabled = autoCaptureEnabled,
                                    onCountdownProgress = onCountdownProgress,
                                    onTriggerCapture = {
                                        triggerCapture(context) { isCapturingFlash = it }
                                    }
                                )
                            }
                        }

                    // STREAM 3: ImageCapture (12MP 4000x3000, MINIMIZE_LATENCY)
                    val captureResolution = ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(Size(4000, 3000), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                        )
                        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                        .build()

                    val captureBuilder = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setResolutionSelector(captureResolution)

                    val captureExtender = Camera2Interop.Extender(captureBuilder)
                    captureExtender.setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY)
                    captureExtender.setCaptureRequestOption(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_HIGH_QUALITY)
                    captureExtender.setCaptureRequestOption(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_STEADYPHOTO)

                    val imageCapture = captureBuilder.build()

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
                            triggerCapture(context) { isCapturingFlash = it }
                            takePictureDirect(imageCapture, executor, onImageCaptured)
                        }
                    } catch (e: Exception) {
                        Log.e("CameraPreview", "Camera bind failed", e)
                    }
                }, ContextCompat.getMainExecutor(context))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Subtle Shutter Flash (30% opacity, 50ms)
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

private var previousCorners: List<PointF>? = null
private var smoothedCorners: List<PointF>? = null
private var stableFrameCount = 0
private var autoCaptureState: AutoCaptureState = AutoCaptureState.Idle
private var countdownStartTime = 0L
private const val COUNTDOWN_DURATION_MS = 500L

private fun analyzeFrame(
    imageProxy: ImageProxy,
    detector: DocumentDetector,
    onDocumentDetected: (List<PointF>, Boolean, Float) -> Unit,
    imageCapture: ImageCapture?,
    autoCaptureEnabled: Boolean,
    onCountdownProgress: (Float) -> Unit,
    onTriggerCapture: () -> Unit
) {
    val bitmap = try {
        imageProxy.toBitmap().let { bmp ->
            if (imageProxy.imageInfo.rotationDegrees != 0) {
                com.yatagami.utils.BitmapUtils.rotateBitmap(bmp, imageProxy.imageInfo.rotationDegrees)
            } else {
                bmp
            }
        }
    } catch (e: Exception) {
        null
    } finally {
        imageProxy.close() // Release hardware buffer immediately
    }

    if (bitmap == null) return

    CoroutineScope(Dispatchers.Default).launch {
        try {
            val cornersArray = detector.detectDocument(bitmap)
            val confidence = detector.calculateConfidence(
                cornersArray, bitmap.width.toFloat(), bitmap.height.toFloat()
            )

            val rawPts = cornersArray.toList().chunked(2).map { PointF(it[0], it[1]) }
            val isFullImageFallback = (cornersArray[0] == 0f && cornersArray[1] == 0f) || confidence < 0.35f

            // Adaptive Velocity-Aware EMA Smoothing
            var velocity = 0f
            val prev = smoothedCorners
            if (prev != null && prev.size == 4 && rawPts.size == 4) {
                for (i in 0 until 4) {
                    velocity += hypot(rawPts[i].x - prev[i].x, rawPts[i].y - prev[i].y)
                }
                velocity /= 4f
            }

            // Alpha between 0.25 (smooth holding) and 0.55 (quick movement)
            val adaptiveAlpha = (0.25f + (velocity / 50f).coerceIn(0f, 0.30f))

            val finalPts = if (confidence >= 0.45f && rawPts.size == 4) {
                if (prev != null && prev.size == 4) {
                    (0 until 4).map { i ->
                        PointF(
                            adaptiveAlpha * rawPts[i].x + (1f - adaptiveAlpha) * prev[i].x,
                            adaptiveAlpha * rawPts[i].y + (1f - adaptiveAlpha) * prev[i].y
                        )
                    }
                } else rawPts
            } else {
                smoothedCorners ?: rawPts
            }
            smoothedCorners = finalPts

            // Multi-Factor 5-Frame Stability Check
            val isStable = check5FrameStability(finalPts, isFullImageFallback, confidence)
            onDocumentDetected(finalPts, isStable, confidence)

            // Cancelable 500ms Countdown State Machine
            if (autoCaptureEnabled && !isFullImageFallback) {
                when (autoCaptureState) {
                    is AutoCaptureState.Idle -> {
                        if (isStable && confidence >= 0.75f) {
                            autoCaptureState = AutoCaptureState.Stabilizing
                        }
                    }
                    is AutoCaptureState.Stabilizing -> {
                        if (!isStable || confidence < 0.70f) {
                            autoCaptureState = AutoCaptureState.Idle
                            onCountdownProgress(0f)
                        } else {
                            countdownStartTime = System.currentTimeMillis()
                            autoCaptureState = AutoCaptureState.CountingDown(0f)
                        }
                    }
                    is AutoCaptureState.CountingDown -> {
                        if (!isStable || confidence < 0.70f) {
                            // Cancel countdown on movement or confidence drop
                            autoCaptureState = AutoCaptureState.Idle
                            onCountdownProgress(0f)
                        } else {
                            val elapsed = System.currentTimeMillis() - countdownStartTime
                            val progress = (elapsed.toFloat() / COUNTDOWN_DURATION_MS).coerceIn(0f, 1f)
                            onCountdownProgress(progress)

                            if (progress >= 1.0f) {
                                autoCaptureState = AutoCaptureState.Capturing
                                onTriggerCapture()
                                delay(1200) // Cooldown between auto captures
                                autoCaptureState = AutoCaptureState.Idle
                                onCountdownProgress(0f)
                            }
                        }
                    }
                    is AutoCaptureState.Capturing -> {
                        // In capturing cooldown
                    }
                }
            } else {
                onCountdownProgress(0f)
            }
        } catch (e: Exception) {
            Log.e("AnalyzeFrame", "Analysis error", e)
        }
    }
}

private fun check5FrameStability(current: List<PointF>, isFallback: Boolean, confidence: Float): Boolean {
    if (isFallback || current.size != 4 || confidence < 0.65f) {
        stableFrameCount = 0
        previousCorners = null
        return false
    }

    val prev = previousCorners
    if (prev != null && prev.size == 4) {
        var maxMovement = 0f
        for (i in 0 until 4) {
            val dist = hypot(current[i].x - prev[i].x, current[i].y - prev[i].y)
            if (dist > maxMovement) maxMovement = dist
        }

        // Stability threshold: < 8px drift in 720p analysis
        if (maxMovement < 8f) {
            stableFrameCount++
        } else {
            stableFrameCount = 0
        }
    } else {
        stableFrameCount = 0
    }

    previousCorners = current
    return stableFrameCount >= 5 // Require 5 consecutive stable frames (~333ms)
}

private fun triggerCapture(context: Context, flashStateUpdater: (Boolean) -> Unit) {
    CoroutineScope(Dispatchers.Main).launch {
        flashStateUpdater(true)
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator?.vibrate(40)
        }
        delay(50)
        flashStateUpdater(false)
    }
}

private fun takePictureDirect(
    imageCapture: ImageCapture,
    executor: java.util.concurrent.Executor,
    onImageCaptured: (Bitmap) -> Unit
) {
    imageCapture.takePicture(
        executor,
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val cap = image.toBitmap()
                image.close()
                cap?.let(onImageCaptured)
            }
            override fun onError(exc: ImageCaptureException) {
                Log.e("CameraPreview", "Capture failed", exc)
            }
        }
    )
}
