package com.yatagami.ui.components.camera

import android.graphics.PointF
import android.util.Log
import androidx.camera.core.ImageProxy
import com.yatagami.opencv.DocumentDetector
import com.yatagami.ui.components.AutoCaptureState
import com.yatagami.utils.BitmapUtils.toRotatedBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.hypot

class FrameAnalyzerHelper(
    private val detector: DocumentDetector
) {
    private var previousCorners: List<PointF>? = null
    private var smoothedCorners: List<PointF>? = null
    private var stableFrameCount = 0
    private var autoCaptureState: AutoCaptureState = AutoCaptureState.Idle
    private var countdownStartTime = 0L
    private val COUNTDOWN_DURATION_MS = 500L
    private val isAnalyzing = AtomicBoolean(false)

    fun analyze(
        imageProxy: ImageProxy,
        onDocumentDetected: (List<PointF>, Boolean, Float, Float) -> Unit,
        autoCaptureEnabled: Boolean,
        onCountdownProgress: (Float) -> Unit,
        onTriggerCapture: () -> Unit
    ) {
        if (!isAnalyzing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        val bitmap = try {
            imageProxy.toRotatedBitmap()
        } catch (e: Exception) {
            null
        } finally {
            imageProxy.close()
        }

        if (bitmap == null) {
            isAnalyzing.set(false)
            return
        }

        CoroutineScope(Dispatchers.Default).launch {
            try {
                val cornersArray = detector.detectDocument(bitmap)
                val confidence = detector.calculateConfidence(
                    cornersArray, bitmap.width.toFloat(), bitmap.height.toFloat()
                )
                val glareRatio = detector.calculateGlareRatio(bitmap)

                // Normalize corner coordinates to [0.0, 1.0] relative to rotated bitmap dimension
                val bw = bitmap.width.toFloat().coerceAtLeast(1f)
                val bh = bitmap.height.toFloat().coerceAtLeast(1f)
                val rawPts = cornersArray.toList().chunked(2).map { 
                    PointF(
                        (it[0] / bw).coerceIn(0f, 1f),
                        (it[1] / bh).coerceIn(0f, 1f)
                    ) 
                }
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

                // Alpha between 0.30 (smooth holding) and 0.65 (quick movement)
                val adaptiveAlpha = (0.30f + (velocity / 0.10f).coerceIn(0f, 0.35f))

                val finalPts = if (confidence >= 0.35f && rawPts.size == 4) {
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

                // Multi-Factor Stability Check
                val isStable = check5FrameStability(finalPts, isFullImageFallback, confidence)
                onDocumentDetected(finalPts, isStable, confidence, glareRatio)

                // Cancelable 500ms Countdown State Machine (Hold if severe glare > 8%)
                val hasSevereGlare = glareRatio > 0.08f
                if (autoCaptureEnabled && !isFullImageFallback && !hasSevereGlare) {
                    when (autoCaptureState) {
                        is AutoCaptureState.Idle -> {
                            if (isStable && confidence >= 0.65f) {
                                autoCaptureState = AutoCaptureState.Stabilizing
                            }
                        }
                        is AutoCaptureState.Stabilizing -> {
                            if (!isStable || confidence < 0.60f) {
                                autoCaptureState = AutoCaptureState.Idle
                                onCountdownProgress(0f)
                            } else {
                                countdownStartTime = System.currentTimeMillis()
                                autoCaptureState = AutoCaptureState.CountingDown(0f)
                            }
                        }
                        is AutoCaptureState.CountingDown -> {
                            if (!isStable || confidence < 0.60f) {
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
                    autoCaptureState = AutoCaptureState.Idle
                    onCountdownProgress(0f)
                }
            } catch (e: Exception) {
                Log.e("FrameAnalyzerHelper", "Analysis error", e)
            } finally {
                isAnalyzing.set(false)
            }
        }
    }

    private fun check5FrameStability(current: List<PointF>, isFallback: Boolean, confidence: Float): Boolean {
        if (isFallback || current.size != 4 || confidence < 0.50f) {
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

            // Stability threshold: < 2.0% drift in normalized coordinates
            if (maxMovement < 0.020f) {
                stableFrameCount++
            } else {
                stableFrameCount = 0
            }
        } else {
            stableFrameCount = 0
        }

        previousCorners = current
        return stableFrameCount >= 4
    }
}
