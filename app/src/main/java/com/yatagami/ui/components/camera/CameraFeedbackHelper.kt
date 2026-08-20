package com.yatagami.ui.components.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import com.yatagami.utils.BitmapUtils.toRotatedBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

object CameraFeedbackHelper {

    fun triggerCaptureFeedback(context: Context, flashStateUpdater: (Boolean) -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            flashStateUpdater(true)
            vibrateDevice(context, 40)
            delay(50)
            flashStateUpdater(false)
        }
    }

    private fun vibrateDevice(context: Context, durationMs: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        } catch (e: Exception) {
            Log.w("CameraFeedback", "Vibration failed", e)
        }
    }

    fun takePictureDirect(
        imageCapture: ImageCapture,
        executor: Executor,
        onImageCaptured: (Bitmap) -> Unit
    ) {
        imageCapture.takePicture(
            executor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val cap = image.toRotatedBitmap()
                    image.close()
                    cap?.let(onImageCaptured)
                }
                override fun onError(exc: ImageCaptureException) {
                    Log.e("CameraFeedback", "Capture failed", exc)
                }
            }
        )
    }

    fun takePictureWithAutoFocusLock(
        camera: Camera?,
        previewView: PreviewView,
        imageCapture: ImageCapture,
        executor: Executor,
        targetPointNorm: PointF? = null,
        onImageCaptured: (Bitmap) -> Unit
    ) {
        val cameraControl = camera?.cameraControl
        if (cameraControl == null) {
            takePictureDirect(imageCapture, executor, onImageCaptured)
            return
        }

        try {
            val fx = (targetPointNorm?.x ?: 0.5f) * previewView.width.coerceAtLeast(1)
            val fy = (targetPointNorm?.y ?: 0.5f) * previewView.height.coerceAtLeast(1)
            val factory = previewView.meteringPointFactory
            val meteringPoint = factory.createPoint(fx, fy)

            val focusAction = FocusMeteringAction.Builder(
                meteringPoint,
                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
            )
            .setAutoCancelDuration(2, TimeUnit.SECONDS)
            .build()

            val focusFuture = cameraControl.startFocusAndMetering(focusAction)
            focusFuture.addListener({
                // Optical focus is now locked on document text -> fire capture!
                takePictureDirect(imageCapture, executor, onImageCaptured)
            }, executor)
        } catch (e: Exception) {
            Log.w("CameraFeedback", "Focus lock failed, taking direct picture", e)
            takePictureDirect(imageCapture, executor, onImageCaptured)
        }
    }
}
