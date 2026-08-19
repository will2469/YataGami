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
            FilterMode.AUTO -> 5
        }
        nativeEnhanceImage(src, modeInt)
    }

    suspend fun enhanceImageDirect(src: Bitmap, dst: Bitmap, mode: FilterMode): Boolean = withContext(Dispatchers.Default) {
        val modeInt = when (mode) {
            FilterMode.NONE -> 0
            FilterMode.GRAYSCALE -> 1
            FilterMode.BLACK_WHITE -> 2
            FilterMode.MAGIC_COLOR -> 3
            FilterMode.SHARPEN -> 4
            FilterMode.AUTO -> 5
        }
        nativeEnhanceImageDirect(src, dst, modeInt)
    }

    suspend fun detectSkewAngle(src: Bitmap): Float = withContext(Dispatchers.Default) {
        nativeDetectSkewAngle(src)
    }

    suspend fun deskewImage(src: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        nativeDeskew(src)
    }

    suspend fun calculateBlurScore(src: Bitmap): Float = withContext(Dispatchers.Default) {
        nativeCalculateBlurScore(src)
    }

    suspend fun isBlurry(src: Bitmap, threshold: Float = 90.0f): Boolean = withContext(Dispatchers.Default) {
        nativeCalculateBlurScore(src) < threshold
    }

    suspend fun calculateGlareRatio(src: Bitmap): Float = withContext(Dispatchers.Default) {
        nativeCalculateGlareRatio(src)
    }

    suspend fun recommendFilter(src: Bitmap): FilterMode = withContext(Dispatchers.Default) {
        when (nativeRecommendFilter(src)) {
            1 -> FilterMode.GRAYSCALE
            2 -> FilterMode.BLACK_WHITE
            3 -> FilterMode.MAGIC_COLOR
            4 -> FilterMode.SHARPEN
            else -> FilterMode.NONE
        }
    }

    fun clearBufferPool() {
        nativeClearBufferPool()
    }

    suspend fun processPageFull(
        src: Bitmap,
        dst: Bitmap,
        outCorners: FloatArray,
        mode: FilterMode,
        dstWidth: Int,
        dstHeight: Int
    ): Boolean = withContext(Dispatchers.Default) {
        val modeInt = when (mode) {
            FilterMode.NONE -> 0
            FilterMode.GRAYSCALE -> 1
            FilterMode.BLACK_WHITE -> 2
            FilterMode.MAGIC_COLOR -> 3
            FilterMode.SHARPEN -> 4
            FilterMode.AUTO -> 5
        }
        nativeProcessPageFull(src, dst, outCorners, modeInt, dstWidth, dstHeight)
    }

    private external fun nativeWarpPerspective(
        srcBitmap: Bitmap, corners: FloatArray, dstWidth: Int, dstHeight: Int
    ): Bitmap

    private external fun nativeEnhanceImage(bitmap: Bitmap, mode: Int): Bitmap
    private external fun nativeEnhanceImageDirect(srcBitmap: Bitmap, dstBitmap: Bitmap, mode: Int): Boolean
    private external fun nativeClearBufferPool()
    private external fun nativeProcessPageFull(
        srcBitmap: Bitmap, dstBitmap: Bitmap, outCorners: FloatArray,
        mode: Int, dstWidth: Int, dstHeight: Int
    ): Boolean

    private external fun nativeDetectSkewAngle(bitmap: Bitmap): Float

    private external fun nativeDeskew(bitmap: Bitmap): Bitmap

    private external fun nativeCalculateBlurScore(bitmap: Bitmap): Float

    private external fun nativeCalculateGlareRatio(bitmap: Bitmap): Float

    private external fun nativeRecommendFilter(bitmap: Bitmap): Int
}
