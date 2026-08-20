package com.yatagami.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Utility object untuk konversi dan manipulasi Bitmap.
 * Semua operasi offline, gak butuh internet.
 */
object BitmapUtils {

    /**
     * Konversi ImageProxy ke Bitmap dengan rotasi yang benar (tepat 1x rotasi).
     */
    fun ImageProxy.toRotatedBitmap(): Bitmap? {
        return try {
            val rawBitmap = this.toBitmap()
            if (rawBitmap != null && imageInfo.rotationDegrees != 0) {
                rotateBitmap(rawBitmap, imageInfo.rotationDegrees)
            } else {
                rawBitmap
            }
        } catch (e: Exception) {
            // Fallback for YUV format if toBitmap() fails
            try {
                if (format == ImageFormat.YUV_420_888) {
                    yuv420888ToBitmap(this)
                } else {
                    null
                }
            } catch (ex: Exception) {
                null
            }
        }
    }

    /**
     * Konversi YUV_420_888 ke Bitmap via YuvImage.
     */
    private fun yuv420888ToBitmap(image: ImageProxy): Bitmap? {
        return try {
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)

            // Copy Y channel
            yBuffer.get(nv21, 0, ySize)

            // Copy VU channel (NV21 format = Y + VU interleaved)
            val pixelStride = image.planes[1].pixelStride
            if (pixelStride == 2) {
                vBuffer.get(nv21, ySize, vSize)
                uBuffer.get(nv21, ySize + vSize, uSize)
            } else {
                uBuffer.get(nv21, ySize, uSize)
                vBuffer.get(nv21, ySize + uSize, vSize)
            }

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(
                Rect(0, 0, image.width, image.height),
                95,
                out
            )
            val jpegBytes = out.toByteArray()

            val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                ?: return null

            if (image.imageInfo.rotationDegrees != 0) {
                rotateBitmap(bitmap, image.imageInfo.rotationDegrees)
            } else {
                bitmap
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Rotasi bitmap berdasarkan derajat (90, 180, 270).
     */
    fun rotateBitmap(source: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return source

        val matrix = Matrix().apply {
            postRotate(degrees.toFloat())
        }
        return Bitmap.createBitmap(
            source,
            0,
            0,
            source.width,
            source.height,
            matrix,
            true
        )
    }

    /**
     * Resize bitmap dengan aspect ratio tetap terjaga.
     */
    fun resizeKeepingAspectRatio(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val ratio = width.toFloat() / height.toFloat()

        val newWidth: Int
        val newHeight: Int
        if (width > height) {
            newWidth = maxDimension
            newHeight = (maxDimension / ratio).toInt()
        } else {
            newHeight = maxDimension
            newWidth = (maxDimension * ratio).toInt()
        }

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * Simpan bitmap ke file temporary (cache directory).
     */
    fun saveBitmapToCache(bitmap: Bitmap, cacheDir: File, fileName: String): File? {
        return try {
            val file = File(cacheDir, fileName)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            file
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Load bitmap dari file cache.
     */
    fun loadBitmapFromFile(file: File): Bitmap? {
        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Crop bitmap ke aspect ratio A4 (1:1.414).
     */
    fun cropToA4Ratio(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val currentRatio = height.toFloat() / width.toFloat()
        val a4Ratio = 1.414f

        return if (currentRatio > a4Ratio) {
            val newHeight = (width * a4Ratio).toInt()
            val top = (height - newHeight) / 2
            Bitmap.createBitmap(bitmap, 0, top, width, newHeight)
        } else {
            val newWidth = (height / a4Ratio).toInt()
            val left = (width - newWidth) / 2
            Bitmap.createBitmap(bitmap, left, 0, newWidth, height)
        }
    }

    /**
     * Flip bitmap horizontal (mirror).
     */
    fun flipHorizontally(bitmap: Bitmap): Bitmap {
        val matrix = Matrix().apply { postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Hitung ukuran file estimasi dalam KB kalau di-compress JPEG.
     */
    fun estimateJpegSize(bitmap: Bitmap, quality: Int): Int {
        return try {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            stream.size() / 1024
        } catch (e: Exception) {
            0
        }
    }
}
