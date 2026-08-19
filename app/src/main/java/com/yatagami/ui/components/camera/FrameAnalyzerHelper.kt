package com.yatagami.ui.components.camera

import android.graphics.PointF
import android.util.Log
import androidx.camera.core.ImageProxy
import com.yatagami.opencv.DocumentDetector
import com.yatagami.ui.components.AutoCaptureState
import com.yatagami.utils.BitmapUtils.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

    fun analyze(
        imageProxy: ImageProxy,
        onDocumentDetected: (List<PointF>, Boolean, Float, Float) -> Unit,
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
                val glareRatio = detector.calculateGlareRatio(bitmap)

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
                onDocumentDetected(finalPts, isStable, confidence, glareRatio)

                // Cancelable 500ms Countdown State Machine (Hold if severe glare > 8%)
                val hasSevereGlare = glareRatio > 0.08f
                if (autoCaptureEnabled && !isFullImageFallback && !hasSevereGlare) {
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
                    if (hasSevereGlare && autoCaptureState is AutoCaptureState.CountingDown) {
                        autoCaptureState = AutoCaptureState.Idle
                    }
                    onCountdownProgress(0f)
                }
            } catch (e: Exception) {
                Log.e("FrameAnalyzerHelper", "Analysis error", e)
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
}
