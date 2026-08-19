package com.yatagami.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object ThumbnailManager {

    private const val THUMB_SIZE = 200
    private const val CACHE_SIZE = 50

    // Memory LRU Cache for immediate UI rendering (<5ms)
    private val memoryCache = object : LruCache<String, Bitmap>(CACHE_SIZE) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return 1 // Count entries
        }
    }

    fun getThumbnailDir(context: Context): File {
        return File(context.cacheDir, "thumbnails").also {
            if (!it.exists()) it.mkdirs()
        }
    }

    suspend fun createAndSaveThumbnail(
        context: Context,
        docId: String,
        coverBitmap: Bitmap,
        updatedAt: Long
    ): String = withContext(Dispatchers.IO) {
        val thumbDir = getThumbnailDir(context)

        // Clean any old thumbnails for this document (cache-busting)
        thumbDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("${docId}_") && file.name.endsWith(".jpg")) {
                file.delete()
            }
        }

        val fileName = "${docId}_${updatedAt}.jpg"
        val thumbFile = File(thumbDir, fileName)

        // Downscale to 200x200 while preserving aspect ratio
        val origW = coverBitmap.width
        val origH = coverBitmap.height
        val scale = minOf(THUMB_SIZE.toFloat() / origW, THUMB_SIZE.toFloat() / origH)
        val targetW = (origW * scale).toInt().coerceAtLeast(1)
        val targetH = (origH * scale).toInt().coerceAtLeast(1)

        val scaledBmp = Bitmap.createScaledBitmap(coverBitmap, targetW, targetH, true)

        try {
            FileOutputStream(thumbFile).use { out ->
                scaledBmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            memoryCache.put(thumbFile.absolutePath, scaledBmp)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        thumbFile.absolutePath
    }

    fun getThumbnailBitmap(path: String): Bitmap? {
        if (path.isBlank()) return null
        
        // 1. Check memory cache
        val cached = memoryCache.get(path)
        if (cached != null && !cached.isRecycled) return cached

        // 2. Decode from disk
        val file = File(path)
        if (!file.exists()) return null

        return try {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            if (bitmap != null) {
                memoryCache.put(path, bitmap)
            }
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    fun invalidateCache(path: String) {
        memoryCache.remove(path)
        try {
            val file = File(path)
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
