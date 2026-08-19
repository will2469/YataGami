package com.yatagami.data.repository

import android.content.Context
import android.util.Log
import com.yatagami.data.db.DocumentLibraryDatabase
import com.yatagami.data.model.LibraryDocument
import com.yatagami.data.session.PageSessionData
import com.yatagami.utils.ThumbnailManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class DocumentLibraryRepository(private val context: Context) {

    private val db = DocumentLibraryDatabase.getInstance(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _documents = MutableStateFlow<List<LibraryDocument>>(emptyList())
    val documents: StateFlow<List<LibraryDocument>> = _documents.asStateFlow()

    // Map of scheduled physical file purge jobs for Undo safety
    private val pendingPurgeJobs = ConcurrentHashMap<String, Job>()

    companion object {
        private const val TAG = "DocLibraryRepo"

        @Volatile
        private var INSTANCE: DocumentLibraryRepository? = null

        fun getInstance(context: Context): DocumentLibraryRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DocumentLibraryRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    init {
        refreshDocuments()
    }

    fun refreshDocuments() {
        scope.launch {
            val list = db.getAllDocuments()
            _documents.value = list
        }
    }

    suspend fun saveDocument(
        doc: LibraryDocument,
        pages: List<PageSessionData> = emptyList()
    ) = withContext(Dispatchers.IO) {
        db.insertOrUpdateDocument(doc, pages)
        refreshDocuments()
    }

    suspend fun renameDocument(
        docId: String,
        newTitle: String
    ) = withContext(Dispatchers.IO) {
        db.updateDocumentTitle(docId, newTitle.trim())
        refreshDocuments()
    }

    suspend fun deleteDocument(docId: String): LibraryDocument? = withContext(Dispatchers.IO) {
        val doc = db.getDocumentById(docId) ?: return@withContext null
        db.deleteDocument(docId)
        refreshDocuments()
        doc
    }

    fun schedulePermanentDelete(doc: LibraryDocument, delayMs: Long = 4000L): Job {
        pendingPurgeJobs[doc.id]?.cancel()

        val job = scope.launch {
            delay(delayMs)
            // Timeout expired, perform physical file cleanup
            try {
                // Delete thumbnail
                if (doc.thumbnailPath.isNotBlank()) {
                    ThumbnailManager.invalidateCache(doc.thumbnailPath)
                }
                // Delete PDF if stored in internal/app storage
                doc.pdfPath?.let { path ->
                    val file = File(path)
                    if (file.exists() && file.isFile) {
                        file.delete()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to purge physical files for ${doc.id}", e)
            } finally {
                pendingPurgeJobs.remove(doc.id)
            }
        }

        pendingPurgeJobs[doc.id] = job
        return job
    }

    suspend fun restoreDocument(doc: LibraryDocument) = withContext(Dispatchers.IO) {
        // Cancel scheduled purge
        pendingPurgeJobs[doc.id]?.cancel()
        pendingPurgeJobs.remove(doc.id)

        // Restore in DB
        val pages = db.getPagesForDocument(doc.id)
        db.insertOrUpdateDocument(doc, pages)
        refreshDocuments()
    }

    suspend fun duplicateDocument(docId: String): Result<LibraryDocument> = withContext(Dispatchers.IO) {
        try {
            val original = db.getDocumentById(docId)
                ?: return@withContext Result.failure(IllegalArgumentException("Document not found"))

            val newId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val newTitle = "${original.title} (Salinan)"

            // 1. Copy thumbnail
            var newThumbPath = ""
            if (original.thumbnailPath.isNotBlank()) {
                val origThumbFile = File(original.thumbnailPath)
                if (origThumbFile.exists()) {
                    val thumbDir = ThumbnailManager.getThumbnailDir(context)
                    val newThumbFile = File(thumbDir, "${newId}_${now}.jpg")
                    origThumbFile.copyTo(newThumbFile, overwrite = true)
                    newThumbPath = newThumbFile.absolutePath
                }
            }

            // 2. Copy PDF if it exists in local file path
            var newPdfPath = original.pdfPath
            if (original.pdfPath != null && !original.pdfPath.startsWith("content://")) {
                val origPdfFile = File(original.pdfPath)
                if (origPdfFile.exists()) {
                    val newPdfFile = File(origPdfFile.parentFile, "${newTitle.replace(' ', '_')}_$now.pdf")
                    origPdfFile.copyTo(newPdfFile, overwrite = true)
                    newPdfPath = newPdfFile.absolutePath
                }
            }

            // 3. Copy page records
            val origPages = db.getPagesForDocument(docId)
            val newPages = origPages.map { p ->
                p.copy(pageId = UUID.randomUUID().toString())
            }

            val duplicated = LibraryDocument(
                id = newId,
                title = newTitle,
                createdAt = now,
                updatedAt = now,
                pageCount = original.pageCount,
                fileSizeBytes = original.fileSizeBytes,
                primaryDocType = original.primaryDocType,
                pdfPath = newPdfPath,
                thumbnailPath = newThumbPath
            )

            db.insertOrUpdateDocument(duplicated, newPages)
            refreshDocuments()
            Result.success(duplicated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getDocumentDetails(docId: String): Pair<LibraryDocument?, List<PageSessionData>> = withContext(Dispatchers.IO) {
        val doc = db.getDocumentById(docId)
        val pages = db.getPagesForDocument(docId)
        doc to pages
    }
}
