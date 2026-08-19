package com.yatagami.data.model

import android.graphics.Bitmap
import java.util.UUID

data class ScannedPage(
    val id: String = UUID.randomUUID().toString(),
    val originalBitmap: Bitmap,
    var croppedBitmap: Bitmap? = null,
    var filterMode: FilterMode = FilterMode.AUTO,
    var processedBitmap: Bitmap? = null,
    val corners: FloatArray = floatArrayOf(),
    var pageNumber: Int = 0,
    var cacheFilePath: String? = null
) {
    fun getDisplayBitmap(): Bitmap {
        return processedBitmap ?: croppedBitmap ?: originalBitmap
    }

    fun recycle() {
        originalBitmap.recycle()
        croppedBitmap?.recycle()
        processedBitmap?.recycle()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ScannedPage
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
