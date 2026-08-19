package com.yatagami.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.yatagami.R

class PdfProcessingService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val CHANNEL_ID = "yatagami_pdf_processing"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "ACTION_START"
        const val ACTION_UPDATE = "ACTION_UPDATE"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_PROGRESS_TEXT = "EXTRA_PROGRESS_TEXT"

        fun start(context: Context, text: String) {
            val intent = Intent(context, PdfProcessingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PROGRESS_TEXT, text)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun updateProgress(context: Context, text: String) {
            val intent = Intent(context, PdfProcessingService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_PROGRESS_TEXT, text)
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, PdfProcessingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Acquire WakeLock to keep Cortex-A76 cores active on aggressive HiOS battery manager
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "YataGami:PdfCompileWakeLock"
        )?.apply {
            setReferenceCounted(false)
            acquire(10 * 60 * 1000L) // 10 minutes maximum safety timeout
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val text = intent.getStringExtra(EXTRA_PROGRESS_TEXT) ?: "Menyusun PDF..."
                startForeground(NOTIFICATION_ID, buildNotification(text))
            }
            ACTION_UPDATE -> {
                val text = intent.getStringExtra(EXTRA_PROGRESS_TEXT) ?: "Memproses dokumen..."
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, buildNotification(text))
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("YataGami Document Processor")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Pemrosesan Dokumen PDF",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifikasi status background saat menyusun dokumen PDF"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
