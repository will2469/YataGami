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

            // Adaptive resolution: 300 DPI (2480x3508) on normal, 200 DPI (1754x2480) on thermal throttling
            val (dstW, dstH) = if (DevicePerformanceMonitor.isThermalThrottling.value) {
                1754 to 2480
            } else {
                2480 to 3508
            }

            val warped = processor.warpPerspective(bitmap, corners, dstW, dstH)
            val page = ScannedPage(
                originalBitmap = bitmap,
                croppedBitmap = warped,
                corners = corners,
                pageNumber = pages.size + 1
            )
            pages.add(page)

            if (isAutoSaveJpg.value) {
                repository.saveSingleImageToGallery(page)
            }

            isProcessing.value = false
            _events.emit(ScanEvent.PageAdded(page.id))
        }
    }

    fun updateFilter(pageId: String, mode: FilterMode) {
        viewModelScope.launch {
            val idx = pages.indexOfFirst { it.id == pageId }
            if (idx == -1) return@launch
            val page = pages[idx]
            isProcessing.value = true
            val enhanced = processor.enhanceImage(page.croppedBitmap ?: page.originalBitmap, mode)
            page.filterMode = mode
            page.processedBitmap = enhanced
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
