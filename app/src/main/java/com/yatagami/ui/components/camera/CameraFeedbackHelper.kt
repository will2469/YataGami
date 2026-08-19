package com.yatagami.ui.components.camera

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import com.yatagami.utils.BitmapUtils.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
                    Log.e("CameraFeedback", "Capture failed", exc)
                }
            }
        )
    }
}
