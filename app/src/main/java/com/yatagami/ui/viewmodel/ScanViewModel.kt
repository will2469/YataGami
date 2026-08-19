package com.yatagami.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yatagami.data.model.FilterMode
import com.yatagami.data.model.ScannedPage
import com.yatagami.data.repository.ScanRepository
import com.yatagami.opencv.DocumentDetector
import com.yatagami.opencv.ImageProcessor
import com.yatagami.utils.DevicePerformanceMonitor
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

class ScanViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ScanRepository(application)
    private val detector = DocumentDetector()
    private val processor = ImageProcessor()

    val pages = mutableStateListOf<ScannedPage>()
    val isProcessing = mutableStateOf(false)
    val currentTitle = mutableStateOf("")
    val isAutoSaveJpg = mutableStateOf(false)

    private val _events = MutableSharedFlow<ScanEvent>()
    val events: SharedFlow<ScanEvent> = _events

    init {
        DevicePerformanceMonitor.init(application)
    }

    fun addPage(bitmap: Bitmap) {
        viewModelScope.launch {
            isProcessing.value = true
            val corners = detector.detectDocument(bitmap)
            val confidence = detector.calculateConfidence(
                corners, bitmap.width.toFloat(), bitmap.height.toFloat()
            )

            // Intelligent 150 DPI aspect ratio and orientation inference
            val docInfo = processor.inferDocumentType(corners)
            val warped = processor.warpPerspective(bitmap, corners, docInfo.targetWidth, docInfo.targetHeight)
            val enhanced = processor.enhanceImage(warped, FilterMode.AUTO)
            val page = ScannedPage(
                originalBitmap = bitmap,
                croppedBitmap = warped,
                filterMode = FilterMode.AUTO,
                docType = docInfo.type,
                isPortrait = docInfo.isPortrait,
                processedBitmap = enhanced,
                originalCorners = corners,
                autoConfidence = confidence,
                pageNumber = pages.size + 1
            )
            pages.add(page)
            repository.cacheOriginalCapture(page)

            if (isAutoSaveJpg.value) {
                repository.saveSingleImageToGallery(page)
            }

            isProcessing.value = false
            _events.emit(ScanEvent.PageAdded(page.id))
        }
    }

    fun updateCropCorners(pageId: String, newCorners: FloatArray): Boolean {
        val idx = pages.indexOfFirst { it.id == pageId }
        if (idx == -1 || newCorners.size < 8) return false
        val page = pages[idx]

        val docInfo = processor.inferDocumentType(newCorners)

        viewModelScope.launch {
            isProcessing.value = true
            val warped = processor.warpPerspective(page.originalBitmap, newCorners, docInfo.targetWidth, docInfo.targetHeight)
            val enhanced = processor.enhanceImage(warped, page.filterMode)

            page.manualCorners = newCorners
            page.isManuallyAdjusted = true
            page.docType = docInfo.type
            page.isPortrait = docInfo.isPortrait
            page.croppedBitmap?.recycle()
            page.processedBitmap?.recycle()
            page.croppedBitmap = warped
            page.processedBitmap = enhanced

            pages[idx] = page.copy()
            isProcessing.value = false
        }
        return true
    }

    fun resetCropToAuto(pageId: String) {
        val idx = pages.indexOfFirst { it.id == pageId }
        if (idx == -1) return
        val page = pages[idx]
        page.manualCorners = null
        page.isManuallyAdjusted = false
        updateCropCorners(pageId, page.originalCorners)
    }

    fun resetCropToFull(pageId: String) {
        val idx = pages.indexOfFirst { it.id == pageId }
        if (idx == -1) return
        val page = pages[idx]
        val w = page.originalBitmap.width.toFloat()
        val h = page.originalBitmap.height.toFloat()
        val fullCorners = floatArrayOf(
            0f, 0f,
            w, 0f,
            w, h,
            0f, h
        )
        updateCropCorners(pageId, fullCorners)
    }

    fun updateFilter(pageId: String, mode: FilterMode) {
        viewModelScope.launch {
            val idx = pages.indexOfFirst { it.id == pageId }
            if (idx == -1) return@launch
            val page = pages[idx]
            isProcessing.value = true
            val src = page.croppedBitmap ?: page.originalBitmap
            val existing = page.processedBitmap
            if (existing != null && existing.width == src.width && existing.height == src.height && !existing.isRecycled) {
                processor.enhanceImageDirect(src, existing, mode)
                page.filterMode = mode
            } else {
                val enhanced = processor.enhanceImage(src, mode)
                page.filterMode = mode
                page.processedBitmap = enhanced
            }
            pages[idx] = page.copy() // trigger recomposition
            isProcessing.value = false
        }
    }

    fun reorderPages(from: Int, to: Int) {
        if (from !in pages.indices || to !in pages.indices) return
        val moved = pages.removeAt(from)
        pages.add(to, moved)
        pages.forEachIndexed { i, p -> p.pageNumber = i + 1 }
    }

    fun deletePage(pageId: String) {
        val idx = pages.indexOfFirst { it.id == pageId }
        if (idx != -1) {
            pages[idx].recycle()
            pages.removeAt(idx)
            pages.forEachIndexed { i, p -> p.pageNumber = i + 1 }
        }
    }

    fun savePdf() {
        viewModelScope.launch {
            isProcessing.value = true
            val result = repository.savePdf(pages, currentTitle.value.ifEmpty { "Document" })
            isProcessing.value = false
            _events.emit(
                if (result.isSuccess) ScanEvent.PdfSaved(result.getOrThrow())
                else ScanEvent.Error(result.exceptionOrNull()?.message ?: "Unknown")
            )
        }
    }

    fun saveAsImages() {
        viewModelScope.launch {
            isProcessing.value = true
            val result = repository.saveImagesToGallery(pages)
            isProcessing.value = false
            _events.emit(
                if (result.isSuccess) ScanEvent.ImagesSaved(result.getOrThrow().size, result.getOrThrow())
                else ScanEvent.Error(result.exceptionOrNull()?.message ?: "Gagal menyimpan gambar")
            )
        }
    }

    override fun onCleared() {
        pages.forEach { it.recycle() }
        pages.clear()
    }
}

sealed class ScanEvent {
    data class PageAdded(val pageId: String) : ScanEvent()
    data class PdfSaved(val path: String) : ScanEvent()
    data class ImagesSaved(val count: Int, val paths: List<String>) : ScanEvent()
    data class Error(val message: String) : ScanEvent()
}
