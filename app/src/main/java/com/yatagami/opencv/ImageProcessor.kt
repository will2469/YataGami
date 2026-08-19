package com.yatagami.opencv

import android.graphics.Bitmap
import com.yatagami.data.model.FilterMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ImageProcessor {
    init { System.loadLibrary("yatagami") }

    suspend fun warpPerspective(
        src: Bitmap, corners: FloatArray, dstWidth: Int, dstHeight: Int
    ): Bitmap = withContext(Dispatchers.Default) {
        nativeWarpPerspective(src, corners, dstWidth, dstHeight)
    }

    suspend fun enhanceImage(src: Bitmap, mode: FilterMode): Bitmap = withContext(Dispatchers.Default) {
        val modeInt = when (mode) {
            FilterMode.NONE -> 0
            FilterMode.GRAYSCALE -> 1
            FilterMode.BLACK_WHITE -> 2
            FilterMode.MAGIC_COLOR -> 3
            FilterMode.SHARPEN -> 4
        }
        nativeEnhanceImage(src, modeInt)
    }

    private external fun nativeWarpPerspective(
        srcBitmap: Bitmap, corners: FloatArray, dstWidth: Int, dstHeight: Int
    ): Bitmap

    private external fun nativeEnhanceImage(bitmap: Bitmap, mode: Int): Bitmap
}
