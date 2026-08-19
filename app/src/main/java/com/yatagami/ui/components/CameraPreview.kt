package com.yatagami.ui.components

import android.graphics.Bitmap
import android.graphics.PointF
import android.hardware.camera2.CaptureRequest
import android.os.Build
import android.util.Log
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
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.yatagami.opencv.DocumentDetector
import com.yatagami.utils.BitmapUtils.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import kotlin.math.hypot

@OptIn(ExperimentalCamera2Interop::class)
@Composable
fun CameraPreview(
    onImageCaptured: (Bitmap) -> Unit,
    onDocumentDetected: (List<PointF>, Boolean) -> Unit,
    autoCaptureEnabled: Boolean = true,
    torchEnabled: Boolean = false,
    onCameraReady: ((() -> Unit) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val detector = remember { DocumentDetector() }

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

                    // 1. Preview Builder with fast MediaTek ISP preview options
                    val previewBuilder = Preview.Builder()
                    val previewExtender = Camera2Interop.Extender(previewBuilder)
                    previewExtender.setCaptureRequestOption(
                        CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                    )
                    previewExtender.setCaptureRequestOption(
                        CaptureRequest.NOISE_REDUCTION_MODE,
                        CaptureRequest.NOISE_REDUCTION_MODE_FAST
                    )
                    val preview = previewBuilder.build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    // 2. ImageCapture Builder optimized for Tecno Pova 7 108MP (12MP 9-in-1 binned sweet spot + ZSL)
                    val resolutionSelector = androidx.camera.core.resolutionselector.ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            androidx.camera.core.resolutionselector.ResolutionStrategy(
                                android.util.Size(4000, 3000),
                                androidx.camera.core.resolutionselector.ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                            )
                        )
                        .setAspectRatioStrategy(androidx.camera.core.resolutionselector.AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                        .build()

                    val captureBuilder = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_ZERO_SHUTTER_LAG)
                        .setResolutionSelector(resolutionSelector)

                    val captureExtender = Camera2Interop.Extender(captureBuilder)
                    // Request MediaTek Hardware Noise Reduction (MNRF / ANRF)
                    captureExtender.setCaptureRequestOption(
                        CaptureRequest.NOISE_REDUCTION_MODE,
                        CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY
                    )
                    // Request MediaTek Hardware ISP Edge Sharpening
                    captureExtender.setCaptureRequestOption(
                        CaptureRequest.EDGE_MODE,
                        CaptureRequest.EDGE_MODE_HIGH_QUALITY
                    )
                    // Hardware Document Steady Capture Optimization
                    captureExtender.setCaptureRequestOption(
                        CaptureRequest.CONTROL_SCENE_MODE,
                        CaptureRequest.CONTROL_SCENE_MODE_STEADYPHOTO
                    )
                    // Hardware Lens Shading Correction
                    captureExtender.setCaptureRequestOption(
                        CaptureRequest.SHADING_MODE,
                        CaptureRequest.SHADING_MODE_HIGH_QUALITY
                    )
                    // Hardware Hot Pixel Correction
                    captureExtender.setCaptureRequestOption(
                        CaptureRequest.HOT_PIXEL_MODE,
                        CaptureRequest.HOT_PIXEL_MODE_HIGH_QUALITY
                    )

                    val imageCapture = captureBuilder.build()

                    // 3. ImageAnalysis Builder for Real-Time Contour Detection
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(executor) { imageProxy ->
                                analyzeFrame(
                                    imageProxy,
                                    detector,
                                    onDocumentDetected,
                                    imageCapture,
                                    executor,
                                    onImageCaptured,
                                    autoCaptureEnabled
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

                        camera.cameraControl.enableTorch(torchEnabled)

                        // Expose manual shutter trigger
                        onCameraReady?.invoke {
                            takeManualPicture(imageCapture, executor, onImageCaptured)
                        }
                    } catch (e: Exception) {
                        Log.e("CameraPreview", "Camera bind failed", e)
                    }
                }, ContextCompat.getMainExecutor(context))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

private var lastAutoCaptureTime = 0L
private const val AUTO_CAPTURE_COOLDOWN = 2500L
private var previousCorners: List<PointF>? = null
private var smoothedCorners: List<PointF>? = null
private var stableFrameCount = 0
private const val EMA_ALPHA = 0.35f

private fun analyzeFrame(
    imageProxy: ImageProxy,
    detector: DocumentDetector,
    onDocumentDetected: (List<PointF>, Boolean) -> Unit,
    imageCapture: ImageCapture,
    executor: java.util.concurrent.Executor,
    onImageCaptured: (Bitmap) -> Unit,
    autoCaptureEnabled: Boolean
) {
    // 1. Fast frame extraction and IMMEDIATE ImageProxy release to avoid camera pipeline backpressure
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
        imageProxy.close() // ALWAYS release camera hardware buffer immediately
    }

    if (bitmap == null) return

    // 2. Asynchronous CV Analysis pipeline on Big Cores
    CoroutineScope(Dispatchers.Default).launch {
        try {
            val cornersArray = detector.detectDocument(bitmap)
            val confidence = detector.calculateConfidence(
                cornersArray, bitmap.width.toFloat(), bitmap.height.toFloat()
            )

            val rawPts = cornersArray.toList().chunked(2).map {
                PointF(it[0], it[1])
            }

            val isFullImageFallback = (cornersArray[0] == 0f && cornersArray[1] == 0f) || confidence < 0.3f

            // EMA Temporal Smoothing: Alpha = 0.35 for responsive yet silky-smooth transitions
            val finalPts = if (confidence >= 0.55f && rawPts.size == 4) {
                val prev = smoothedCorners
                if (prev != null && prev.size == 4) {
                    (0 until 4).map { i ->
                        PointF(
                            EMA_ALPHA * rawPts[i].x + (1f - EMA_ALPHA) * prev[i].x,
                            EMA_ALPHA * rawPts[i].y + (1f - EMA_ALPHA) * prev[i].y
                        )
                    }
                } else {
                    rawPts
                }
            } else {
                // Low confidence: retain previous position to avoid jumpy jitter
                smoothedCorners ?: rawPts
            }
            smoothedCorners = finalPts

            val isStable = checkCornerStability(finalPts, isFullImageFallback, confidence)

            onDocumentDetected(finalPts, isStable)

            val now = System.currentTimeMillis()
            if (autoCaptureEnabled && isStable && !isFullImageFallback && confidence >= 0.70f && (now - lastAutoCaptureTime > AUTO_CAPTURE_COOLDOWN)) {
                lastAutoCaptureTime = now
                takeManualPicture(imageCapture, executor, onImageCaptured)
            }
        } catch (e: Exception) {
            Log.e("AnalyzeFrame", "Detection error", e)
        }
    }
}

private fun checkCornerStability(current: List<PointF>, isFallback: Boolean, confidence: Float): Boolean {
    if (isFallback || current.size != 4 || confidence < 0.65f) {
        stableFrameCount = 0
        previousCorners = null
        return false
    }

    val prev = previousCorners
    if (prev != null && prev.size == 4) {
        var maxMovement = 0f
        for (i in 0 until 4) {
            val dx = current[i].x - prev[i].x
            val dy = current[i].y - prev[i].y
            val dist = hypot(dx, dy)
            if (dist > maxMovement) maxMovement = dist
        }

        // Stability threshold: points move < 15 pixels between frames
        if (maxMovement < 15f) {
            stableFrameCount++
        } else {
            stableFrameCount = 0
        }
    } else {
        stableFrameCount = 0
    }

    previousCorners = current
    return stableFrameCount >= 3
}

private fun takeManualPicture(
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
                Log.e("CameraPreview", "Picture capture failed", exc)
            }
        }
    )
}
