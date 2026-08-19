package com.yatagami.opencv

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DocumentDetector {
    init { System.loadLibrary("yatagami") }

    suspend fun detectDocument(bitmap: Bitmap): FloatArray = withContext(Dispatchers.Default) {
        nativeDetectDocument(bitmap)
    }

    private external fun nativeDetectDocument(bitmap: Bitmap): FloatArray
}
