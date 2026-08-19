package com.yatagami.opencv

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DocumentDetector {
    init { System.loadLibrary("yatagami") }

    private val directPointBuffer: ByteBuffer = ByteBuffer.allocateDirect(8 * 4)
        .order(ByteOrder.nativeOrder())
    private val cachedFloats = FloatArray(8)

    suspend fun detectDocument(bitmap: Bitmap): FloatArray = withContext(Dispatchers.Default) {
        directPointBuffer.rewind()
        val success = nativeDetectDocumentDirect(bitmap, directPointBuffer)
        if (success) {
            directPointBuffer.rewind()
            directPointBuffer.asFloatBuffer().get(cachedFloats)
            cachedFloats.clone()
        } else {
            floatArrayOf(
                0f, 0f,
                bitmap.width.toFloat(), 0f,
                bitmap.width.toFloat(), bitmap.height.toFloat(),
                0f, bitmap.height.toFloat()
            )
        }
    }

    private external fun nativeDetectDocument(bitmap: Bitmap): FloatArray
    private external fun nativeDetectDocumentDirect(bitmap: Bitmap, directBuffer: ByteBuffer): Boolean
}
