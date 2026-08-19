package com.yatagami.data.session

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.yatagami.data.model.PageStatus
import com.yatagami.data.model.ScannedPage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

class DocumentSessionManager(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private var saveJob: Job? = null

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        encodeDefaults = true
    }

    private val sessionsDir: File
        get() = File(context.filesDir, "sessions").also { if (!it.exists()) it.mkdirs() }

    private val activeSessionFile: File
        get() = File(sessionsDir, "active_session.json")

    companion object {
        private const val TAG = "DocSessionManager"
        private const val SEVEN_DAYS_MS = 7 * 24 * 60 * 60 * 1000L

        @Volatile
        private var INSTANCE: DocumentSessionManager? = null

        fun getInstance(context: Context): DocumentSessionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DocumentSessionManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    fun createNewSession(title: String = ""): DocumentSession {
        val sessionId = UUID.randomUUID().toString()
        val session = DocumentSession(
            sessionId = sessionId,
            title = title,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            status = SessionStatus.DRAFT,
            pages = emptyList()
        )
        val sessionFolder = File(sessionsDir, sessionId)
        if (!sessionFolder.exists()) sessionFolder.mkdirs()
        return session
    }

    // Debounced async auto-save for metadata changes (500ms delay)
    fun requestSave(session: DocumentSession) {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(500)
            persistSessionMetadata(session)
        }
    }

    // Force save metadata immediately (e.g. onPause / onStop)
    suspend fun forceSave(session: DocumentSession) = withContext(ioDispatcher) {
        saveJob?.cancel()
        persistSessionMetadata(session)
    }

    private fun persistSessionMetadata(session: DocumentSession) {
        try {
            val updated = session.copy(updatedAt = System.currentTimeMillis())
            val jsonString = json.encodeToString(updated)
            
            // 1. Save session-specific active_session.json
            val sessionDir = File(sessionsDir, session.sessionId)
            if (!sessionDir.exists()) sessionDir.mkdirs()
            val specificFile = File(sessionDir, "active_session.json")
            atomicWriteFile(specificFile, jsonString)

            // 2. Update global active pointer
            atomicWriteFile(activeSessionFile, jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist session metadata", e)
        }
    }

    private fun atomicWriteFile(file: File, data: String) {
        file.parentFile?.mkdirs()
        val tempFile = File(file.parentFile, "${file.name}.tmp")
        try {
            FileOutputStream(tempFile).use { fos ->
                fos.write(data.toByteArray(Charsets.UTF_8))
                fos.flush()
                fos.fd.sync()
            }
            if (!tempFile.renameTo(file)) {
                tempFile.copyTo(file, overwrite = true)
                tempFile.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Atomic write failed for ${file.absolutePath}", e)
            tempFile.delete()
        }
    }

    // Persist page bitmap files to filesDir/sessions/{sessionId}/
    suspend fun persistPageBitmaps(
        sessionId: String,
        page: ScannedPage
    ): PageSessionData = withContext(ioDispatcher) {
        val sessionFolder = File(sessionsDir, sessionId).also { if (!it.exists()) it.mkdirs() }
        
        // 1. Raw image
        val rawRelPath = "sessions/$sessionId/page_${page.id}_raw.jpg"
        val rawFile = File(context.filesDir, rawRelPath)
        if (!rawFile.exists() && !page.originalBitmap.isRecycled) {
            saveBitmapToDisk(rawFile, page.originalBitmap, quality = 95)
        }

        // 2. Warped image
        var warpedRelPath: String? = null
        page.croppedBitmap?.let { bmp ->
            if (!bmp.isRecycled) {
                val relPath = "sessions/$sessionId/page_${page.id}_warped.jpg"
                val file = File(context.filesDir, relPath)
                saveBitmapToDisk(file, bmp, quality = 92)
                warpedRelPath = relPath
            }
        }

        // 3. Processed image
        var processedRelPath: String? = null
        page.processedBitmap?.let { bmp ->
            if (!bmp.isRecycled) {
                val relPath = "sessions/$sessionId/page_${page.id}_processed.jpg"
                val file = File(context.filesDir, relPath)
                saveBitmapToDisk(file, bmp, quality = 90)
                processedRelPath = relPath
            }
        }

        PageSessionData(
            pageId = page.id,
            pageNumber = page.pageNumber,
            docType = page.docType,
            isPortrait = page.isPortrait,
            orientationDegrees = page.orientationDegrees,
            filterMode = page.filterMode,
            originalCorners = page.originalCorners.toList(),
            manualCorners = page.manualCorners?.toList(),
            isManuallyAdjusted = page.isManuallyAdjusted,
            autoConfidence = page.autoConfidence,
            rawPath = rawRelPath,
            warpedPath = warpedRelPath,
            processedPath = processedRelPath
        )
    }

    private fun saveBitmapToDisk(file: File, bitmap: Bitmap, quality: Int) {
        val tempFile = File(file.parentFile, "${file.name}.tmp")
        try {
            FileOutputStream(tempFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, fos)
                fos.flush()
                fos.fd.sync()
            }
            if (!tempFile.renameTo(file)) {
                tempFile.copyTo(file, overwrite = true)
                tempFile.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save bitmap to disk: ${file.name}", e)
            tempFile.delete()
        }
    }

    // Check if a valid, restorable draft session exists
    fun hasValidDraftSession(): Boolean {
        val draft = getDraftSession() ?: return false

        // 1. Status must be DRAFT
        if (draft.status != SessionStatus.DRAFT) return false

        // 2. Age < 7 days
        val age = System.currentTimeMillis() - draft.updatedAt
        if (age > SEVEN_DAYS_MS || draft.pages.isEmpty()) {
            clearSession(draft.sessionId)
            return false
        }

        // 3. Physical raw files must exist
        val allFilesExist = draft.pages.all { p ->
            File(context.filesDir, p.rawPath).exists()
        }
        if (!allFilesExist) {
            clearSession(draft.sessionId)
            return false
        }

        return true
    }

    fun getDraftSession(): DocumentSession? {
        if (!activeSessionFile.exists()) return null
        return try {
            val content = activeSessionFile.readText()
            json.decodeFromString<DocumentSession>(content)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode active draft session", e)
            null
        }
    }

    // Restore ScannedPage objects from disk files into memory
    suspend fun restorePagesFromDraft(draft: DocumentSession): List<ScannedPage> = withContext(ioDispatcher) {
        val restored = mutableListOf<ScannedPage>()
        for (pageData in draft.pages.sortedBy { it.pageNumber }) {
            val rawFile = File(context.filesDir, pageData.rawPath)
            if (!rawFile.exists()) continue

            val rawBitmap = BitmapFactory.decodeFile(rawFile.absolutePath) ?: continue
            val warpedBitmap = pageData.warpedPath?.let { path ->
                val f = File(context.filesDir, path)
                if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
            }
            val processedBitmap = pageData.processedPath?.let { path ->
                val f = File(context.filesDir, path)
                if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
            }

            restored.add(
                ScannedPage(
                    id = pageData.pageId,
                    originalBitmap = rawBitmap,
                    croppedBitmap = warpedBitmap,
                    processedBitmap = processedBitmap,
                    filterMode = pageData.filterMode,
                    docType = pageData.docType,
                    isPortrait = pageData.isPortrait,
                    orientationDegrees = pageData.orientationDegrees,
                    originalCorners = pageData.originalCorners.toFloatArray(),
                    manualCorners = pageData.manualCorners?.toFloatArray(),
                    isManuallyAdjusted = pageData.isManuallyAdjusted,
                    autoConfidence = pageData.autoConfidence,
                    pageNumber = pageData.pageNumber,
                    status = PageStatus.PROCESSED,
                    cacheFilePath = rawFile.absolutePath
                )
            )
        }
        restored
    }

    // Clear session files and metadata
    fun clearSession(sessionId: String) {
        scope.launch {
            try {
                val sessionDir = File(sessionsDir, sessionId)
                if (sessionDir.exists()) {
                    sessionDir.deleteRecursively()
                }
                if (activeSessionFile.exists()) {
                    activeSessionFile.delete()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear session $sessionId", e)
            }
        }
    }

    // Clean expired draft or completed sessions (> 7 days) in background
    fun cleanExpiredSessions() {
        scope.launch {
            try {
                val now = System.currentTimeMillis()
                sessionsDir.listFiles()?.forEach { dir ->
                    if (dir.isDirectory) {
                        val sessFile = File(dir, "active_session.json")
                        if (sessFile.exists()) {
                            try {
                                val sess = json.decodeFromString<DocumentSession>(sessFile.readText())
                                val isOld = (now - sess.updatedAt) > SEVEN_DAYS_MS
                                val isDone = sess.status == SessionStatus.COMPLETED || sess.status == SessionStatus.ABANDONED
                                if (isOld || isDone) {
                                    dir.deleteRecursively()
                                }
                            } catch (e: Exception) {
                                dir.deleteRecursively()
                            }
                        } else {
                            // Empty or orphaned folder older than 1 day
                            if ((now - dir.lastModified()) > TimeUnit.DAYS.toMillis(1)) {
                                dir.deleteRecursively()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Session cleanup failed", e)
            }
        }
    }
}
