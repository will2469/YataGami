package com.yatagami.ui.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yatagami.data.model.DocumentType
import com.yatagami.data.model.FilterMode
import com.yatagami.data.model.ImageExportFormat
import com.yatagami.data.model.LibraryDocument
import com.yatagami.data.model.PdfCompressionTier
import com.yatagami.data.model.ScannedPage
import com.yatagami.data.model.SharePayload
import com.yatagami.data.repository.DocumentLibraryRepository
import com.yatagami.data.repository.ScanRepository
import com.yatagami.data.session.DocumentSession
import com.yatagami.data.session.DocumentSessionManager
import com.yatagami.opencv.DocumentDetector
import com.yatagami.opencv.ImageProcessor
import com.yatagami.utils.DevicePerformanceMonitor
import com.yatagami.utils.ThumbnailManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class ScanViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ScanRepository(application)
    private val sessionManager = DocumentSessionManager.getInstance(application)
    private val libraryRepo = DocumentLibraryRepository.getInstance(application)
    private val detector = DocumentDetector()
    private val processor = ImageProcessor()

    private var currentSession: DocumentSession = sessionManager.createNewSession()

    val pages = mutableStateListOf<ScannedPage>()
    val isProcessing = mutableStateOf(false)
    val currentTitle = mutableStateOf("")
    val isAutoSaveJpg = mutableStateOf(false)

    val draftSession = mutableStateOf<DocumentSession?>(null)
    val showDraftDialog = mutableStateOf(false)

    private val _events = MutableSharedFlow<ScanEvent>()
    val events: SharedFlow<ScanEvent> = _events

    init {
        DevicePerformanceMonitor.init(application)
        sessionManager.cleanExpiredSessions()
        checkDraftSession()
    }

    fun checkDraftSession() {
        viewModelScope.launch(Dispatchers.IO) {
            if (sessionManager.hasValidDraftSession()) {
                val draft = sessionManager.getDraftSession()
                if (draft != null && draft.pages.isNotEmpty()) {
                    draftSession.value = draft
                    showDraftDialog.value = true
                }
            }
        }
    }

    fun resumeDraftSession() {
        val draft = draftSession.value ?: return
        viewModelScope.launch {
            isProcessing.value = true
            val restoredPages = sessionManager.restorePagesFromDraft(draft)
            pages.clear()
            pages.addAll(restoredPages)
            currentSession = draft
            currentTitle.value = draft.title
            showDraftDialog.value = false
            draftSession.value = null
            isProcessing.value = false
        }
    }

    fun discardDraftSession() {
        val draft = draftSession.value
        if (draft != null) {
            sessionManager.clearSession(draft.sessionId)
        }
        showDraftDialog.value = false
        draftSession.value = null
        currentSession = sessionManager.createNewSession()
    }

    fun forceSaveSession() {
        viewModelScope.launch(Dispatchers.IO) {
            sessionManager.forceSave(currentSession)
        }
    }

    private fun syncSessionPages() {
        viewModelScope.launch(Dispatchers.IO) {
            val pageDatas = pages.map { page ->
                sessionManager.persistPageBitmaps(currentSession.sessionId, page)
            }
            currentSession = currentSession.copy(
                title = currentTitle.value,
                pages = pageDatas,
                updatedAt = System.currentTimeMillis()
            )
            sessionManager.requestSave(currentSession)
        }
    }

    fun addPage(bitmap: Bitmap) {
        viewModelScope.launch {
            isProcessing.value = true
            val corners = detector.detectDocument(bitmap)
            val confidence = detector.calculateConfidence(
                corners, bitmap.width.toFloat(), bitmap.height.toFloat()
            )

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

            syncSessionPages()

            if (isAutoSaveJpg.value) {
                repository.saveSingleImageToGallery(page)
            }

            isProcessing.value = false
            _events.emit(ScanEvent.PageAdded(page.id))
        }
    }

    fun importImagesFromUris(context: Context, uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            isProcessing.value = true
            for (uri in uris) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val bmp = BitmapFactory.decodeStream(stream)
                        if (bmp != null) {
                            withContext(Dispatchers.Main) {
                                addPage(bmp)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            isProcessing.value = false
        }
    }

    fun importPdfFromUri(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            isProcessing.value = true
            try {
                val tempFile = File(context.cacheDir, "temp_import_${System.currentTimeMillis()}.pdf")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                val pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = android.graphics.pdf.PdfRenderer(pfd)
                val pageCount = renderer.pageCount
                for (i in 0 until pageCount) {
                    val page = renderer.openPage(i)
                    val w = page.width * 2
                    val h = page.height * 2
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    withContext(Dispatchers.Main) {
                        addPage(bmp)
                    }
                }
                renderer.close()
                pfd.close()
                tempFile.delete()
            } catch (e: Exception) {
                e.printStackTrace()
                _events.emit(ScanEvent.Error("Gagal mengimpor PDF: ${e.localizedMessage}"))
            } finally {
                isProcessing.value = false
            }
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
            syncSessionPages()
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
            pages[idx] = page.copy()
            syncSessionPages()
            isProcessing.value = false
        }
    }

    fun rotatePage(pageId: String) {
        val idx = pages.indexOfFirst { it.id == pageId }
        if (idx == -1) return
        val page = pages[idx]

        viewModelScope.launch(Dispatchers.Default) {
            isProcessing.value = true
            val rotCropped = page.croppedBitmap?.let { com.yatagami.utils.BitmapUtils.rotateBitmap(it, 90) }
            val rotProcessed = page.processedBitmap?.let { com.yatagami.utils.BitmapUtils.rotateBitmap(it, 90) }

            page.croppedBitmap?.recycle()
            page.processedBitmap?.recycle()

            page.croppedBitmap = rotCropped
            page.processedBitmap = rotProcessed
            page.orientationDegrees = (page.orientationDegrees + 90) % 360

            pages[idx] = page.copy()
            syncSessionPages()
            isProcessing.value = false
        }
    }

    fun movePage(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in pages.indices || toIndex !in pages.indices || fromIndex == toIndex) return
        val moved = pages.removeAt(fromIndex)
        pages.add(toIndex, moved)
        pages.forEachIndexed { i, p -> p.pageNumber = i + 1 }
        syncSessionPages()
    }

    fun restorePage(page: ScannedPage, atIndex: Int) {
        val targetIdx = atIndex.coerceIn(0, pages.size)
        pages.add(targetIdx, page)
        pages.forEachIndexed { i, p -> p.pageNumber = i + 1 }
        syncSessionPages()
    }

    fun deletePage(pageId: String): Pair<ScannedPage, Int>? {
        val idx = pages.indexOfFirst { it.id == pageId }
        if (idx != -1) {
            val removed = pages.removeAt(idx)
            pages.forEachIndexed { i, p -> p.pageNumber = i + 1 }
            syncSessionPages()
            return removed to idx
        }
        return null
    }

    fun generateSuggestedTitle(): String {
        return repository.generateDefaultDocumentTitle(pages)
    }

    fun exportPdf(
        selectedPageIds: Set<String>? = null,
        compressionTier: PdfCompressionTier = PdfCompressionTier.STANDARD,
        customTitle: String = ""
    ) {
        viewModelScope.launch {
            val targetPages = if (selectedPageIds.isNullOrEmpty()) {
                pages.toList()
            } else {
                pages.filter { it.id in selectedPageIds }
            }
            if (targetPages.isEmpty()) {
                _events.emit(ScanEvent.Error("Pilih minimal 1 halaman untuk diekspor"))
                return@launch
            }

            isProcessing.value = true
            val titleToUse = customTitle.ifBlank { generateSuggestedTitle() }
            val result = repository.savePdf(targetPages, titleToUse, compressionTier = compressionTier)
            isProcessing.value = false

            if (result.isSuccess) {
                val pdfPath = result.getOrThrow()
                val docId = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                val firstPage = targetPages.firstOrNull()
                val thumbPath = if (firstPage != null) {
                    ThumbnailManager.createAndSaveThumbnail(
                        getApplication(),
                        docId,
                        firstPage.getDisplayBitmap(),
                        now
                    )
                } else ""

                val doc = LibraryDocument(
                    id = docId,
                    title = titleToUse,
                    createdAt = now,
                    updatedAt = now,
                    pageCount = targetPages.size,
                    fileSizeBytes = 0L,
                    primaryDocType = firstPage?.docType ?: DocumentType.A4,
                    pdfPath = pdfPath,
                    thumbnailPath = thumbPath
                )
                val sessionPages = targetPages.map { sessionManager.persistPageBitmaps(currentSession.sessionId, it) }
                libraryRepo.saveDocument(doc, sessionPages)

                sessionManager.clearSession(currentSession.sessionId)
                currentSession = sessionManager.createNewSession()
                _events.emit(ScanEvent.PdfSaved(pdfPath))
            } else {
                _events.emit(ScanEvent.Error(result.exceptionOrNull()?.message ?: "Gagal menyimpan PDF"))
            }
        }
    }

    fun sharePdf(
        selectedPageIds: Set<String>? = null,
        compressionTier: PdfCompressionTier = PdfCompressionTier.STANDARD,
        customTitle: String = ""
    ) {
        viewModelScope.launch {
            val targetPages = if (selectedPageIds.isNullOrEmpty()) {
                pages.toList()
            } else {
                pages.filter { it.id in selectedPageIds }
            }
            if (targetPages.isEmpty()) {
                _events.emit(ScanEvent.Error("Pilih minimal 1 halaman untuk dibagikan"))
                return@launch
            }

            isProcessing.value = true
            val titleToUse = customTitle.ifBlank { generateSuggestedTitle() }
            val result = repository.createTempPdfForShare(targetPages, titleToUse, compressionTier)
            isProcessing.value = false

            if (result.isSuccess) {
                val uri = result.getOrThrow()
                _events.emit(
                    ScanEvent.SharePayloadReady(
                        SharePayload(
                            uris = listOf(uri),
                            mimeType = "application/pdf",
                            title = titleToUse,
                            isMultiple = false
                        )
                    )
                )
            } else {
                _events.emit(ScanEvent.Error(result.exceptionOrNull()?.message ?: "Gagal menyiapkan file PDF"))
            }
        }
    }

    fun exportImages(
        selectedPageIds: Set<String>? = null,
        format: ImageExportFormat = ImageExportFormat.JPG_90,
        customTitle: String? = null
    ) {
        viewModelScope.launch {
            val targetPages = if (selectedPageIds.isNullOrEmpty()) {
                pages.toList()
            } else {
                pages.filter { it.id in selectedPageIds }
            }
            if (targetPages.isEmpty()) {
                _events.emit(ScanEvent.Error("Pilih minimal 1 halaman untuk disimpan"))
                return@launch
            }

            isProcessing.value = true
            val titleToUse = customTitle?.ifBlank { null } ?: generateSuggestedTitle()
            val result = repository.saveImagesToGallery(targetPages, format, titleToUse)
            isProcessing.value = false

            if (result.isSuccess) {
                val savedList = result.getOrThrow()
                _events.emit(ScanEvent.ImagesSaved(savedList.size, savedList))
            } else {
                _events.emit(ScanEvent.Error(result.exceptionOrNull()?.message ?: "Gagal menyimpan gambar"))
            }
        }
    }

    fun shareImages(
        selectedPageIds: Set<String>? = null,
        format: ImageExportFormat = ImageExportFormat.JPG_90,
        customTitle: String? = null
    ) {
        viewModelScope.launch {
            val targetPages = if (selectedPageIds.isNullOrEmpty()) {
                pages.toList()
            } else {
                pages.filter { it.id in selectedPageIds }
            }
            if (targetPages.isEmpty()) {
                _events.emit(ScanEvent.Error("Pilih minimal 1 halaman untuk dibagikan"))
                return@launch
            }

            isProcessing.value = true
            val titleToUse = customTitle?.ifBlank { null } ?: generateSuggestedTitle()
            val result = repository.createTempImagesForShare(targetPages, titleToUse, format)
            isProcessing.value = false

            if (result.isSuccess) {
                val uris = result.getOrThrow()
                _events.emit(
                    ScanEvent.SharePayloadReady(
                        SharePayload(
                            uris = uris,
                            mimeType = format.mimeType,
                            title = titleToUse,
                            isMultiple = uris.size > 1
                        )
                    )
                )
            } else {
                _events.emit(ScanEvent.Error(result.exceptionOrNull()?.message ?: "Gagal menyiapkan gambar"))
            }
        }
    }

    fun clearPages() {
        pages.forEach { it.recycle() }
        pages.clear()
        currentSession = sessionManager.createNewSession()
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
    data class SharePayloadReady(val payload: SharePayload) : ScanEvent()
    data class Error(val message: String) : ScanEvent()
}
