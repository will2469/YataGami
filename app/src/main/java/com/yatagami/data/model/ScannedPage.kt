package com.yatagami.data.model

import android.graphics.Bitmap
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class DocumentType(val displayName: String) {
    A4("A4 Dokumen"),
    KTP("KTP / ID Card"),
    F4("F4 / Folio"),
    RECEIPT("Struk / Bon"),
    SQUARE("Foto Persegi"),
    FREEFORM("Kustom")
}

@Serializable
enum class PageStatus {
    CAPTURED,
    WARPED,
    PROCESSING,
    PROCESSED,
    ERROR
}


data class ScannedPage(
    val id: String = UUID.randomUUID().toString(),
    val originalBitmap: Bitmap,
    var croppedBitmap: Bitmap? = null,
    var filterMode: FilterMode = FilterMode.AUTO,
    var docType: DocumentType = DocumentType.A4,
    var isPortrait: Boolean = true,
    var processedBitmap: Bitmap? = null,
    var originalCorners: FloatArray = floatArrayOf(),
    var manualCorners: FloatArray? = null,
    var isManuallyAdjusted: Boolean = false,
    var autoConfidence: Float = 0.0f,
    var pageNumber: Int = 0,
    var orientationDegrees: Int = 0,
    var status: PageStatus = PageStatus.PROCESSED,
    var cacheFilePath: String? = null
) {
    val corners: FloatArray
        get() = manualCorners ?: originalCorners

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
