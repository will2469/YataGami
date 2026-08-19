package com.yatagami.data.session

import com.yatagami.data.model.DocumentType
import com.yatagami.data.model.FilterMode
import kotlinx.serialization.Serializable

@Serializable
enum class SessionStatus {
    DRAFT,
    COMPLETED,
    ABANDONED
}

@Serializable
data class PageSessionData(
    val pageId: String,
    val pageNumber: Int,
    val docType: DocumentType = DocumentType.A4,
    val isPortrait: Boolean = true,
    val orientationDegrees: Int = 0,
    val filterMode: FilterMode = FilterMode.AUTO,
    val originalCorners: List<Float> = emptyList(),
    val manualCorners: List<Float>? = null,
    val isManuallyAdjusted: Boolean = false,
    val autoConfidence: Float = 0.0f,
    val rawPath: String, // Relative path, e.g. "sessions/{sessionId}/page_{id}_raw.jpg"
    val warpedPath: String? = null,
    val processedPath: String? = null,
    val previewPath: String? = null
)

@Serializable
data class DocumentSession(
    val sessionId: String,
    val title: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val status: SessionStatus = SessionStatus.DRAFT,
    val pages: List<PageSessionData> = emptyList()
)
