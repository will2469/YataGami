package com.yatagami.ui.components

import android.util.Log
import android.view.ViewGroup
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

@Composable
fun CameraPreview(
    onImageCaptured: (android.graphics.Bitmap) -> Unit,
    onDocumentDetected: (List<android.graphics.PointF>) -> Unit,
    autoCaptureEnabled: Boolean = true
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
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    val provider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(executor) { imageProxy ->
                                if (autoCaptureEnabled) {
                                    analyzeForAutoCapture(imageProxy, detector, onDocumentDetected, imageCapture, executor, onImageCaptured)
                                } else {
                                    imageProxy.close()
                                }
                            }
                        }

                    try {
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageCapture,
                            imageAnalysis
                        )
                    } catch (e: Exception) {
                        Log.e("CameraPreview", "Bind failed", e)
                    }

                    previewView.tag = imageCapture
                }, ContextCompat.getMainExecutor(context))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

private var lastAutoCaptureTime = 0L
private const val AUTO_CAPTURE_COOLDOWN = 2000L

private fun analyzeForAutoCapture(
    imageProxy: ImageProxy,
    detector: DocumentDetector,
    onDocumentDetected: (List<android.graphics.PointF>) -> Unit,
    imageCapture: ImageCapture,
    executor: java.util.concurrent.Executor,
    onImageCaptured: (android.graphics.Bitmap) -> Unit
) {
    val bitmap = imageProxy.toBitmap() ?: run {
        imageProxy.close()
        return
    }

    CoroutineScope(Dispatchers.Default).launch {
        try {
            val corners = detector.detectDocument(bitmap)
            val pts = corners.toList().chunked(2).map {
                android.graphics.PointF(it[0], it[1])
            }
            onDocumentDetected(pts)

            // Auto-capture kalau dokumen terdeteksi stabil (bukan fallback full image)
            val isFullImage = (corners[0] == 0f && corners[1] == 0f)
            val now = System.currentTimeMillis()
            if (!isFullImage && (now - lastAutoCaptureTime > AUTO_CAPTURE_COOLDOWN)) {
                lastAutoCaptureTime = now
                imageCapture.takePicture(
                    executor,
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val cap = image.toBitmap()
                            image.close()
                            cap?.let(onImageCaptured)
                        }
                        override fun onError(exc: ImageCaptureException) {
                            Log.e("AutoCapture", "Gagal", exc)
                        }
                    }
                )
            }
        } catch (e: Exception) {
            Log.e("Analyze", "Error deteksi", e)
        } finally {
            imageProxy.close()
        }
    }
}

