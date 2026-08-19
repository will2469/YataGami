package com.yatagami.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yatagami.data.model.DateCategory
import com.yatagami.data.model.DocumentType
import com.yatagami.data.model.LibraryDocument
import com.yatagami.data.model.SharePayload
import com.yatagami.data.repository.DocumentLibraryRepository
import com.yatagami.utils.ShareHelper
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DocumentLibraryRepository.getInstance(application)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategoryTag = MutableStateFlow<DocumentType?>(null)
    val selectedCategoryTag: StateFlow<DocumentType?> = _selectedCategoryTag.asStateFlow()

    private val _isGridView = MutableStateFlow(false)
    val isGridView: StateFlow<Boolean> = _isGridView.asStateFlow()

    private val _isDuplicating = MutableStateFlow(false)
    val isDuplicating: StateFlow<Boolean> = _isDuplicating.asStateFlow()

    private val _events = MutableSharedFlow<LibraryEvent>()
    val events: SharedFlow<LibraryEvent> = _events

    val totalDocumentCount: StateFlow<Int> = repository.documents
        .combine(MutableStateFlow(Unit)) { docs, _ -> docs.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    @OptIn(FlowPreview::class)
    val groupedDocuments: StateFlow<Map<DateCategory, List<LibraryDocument>>> =
        combine(
            repository.documents,
            _searchQuery.debounce(200),
            _selectedCategoryTag
        ) { docs, query, tag ->
            val trimmedQuery = query.trim()
            val filtered = docs.filter { doc ->
                val matchesQuery = trimmedQuery.isBlank() || doc.title.contains(trimmedQuery, ignoreCase = true)
                val matchesTag = tag == null || doc.primaryDocType == tag
                matchesQuery && matchesTag
            }
            filtered.groupBy { it.dateCategory }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyMap()
        )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onCategoryTagSelected(tag: DocumentType?) {
        _selectedCategoryTag.value = tag
    }

    fun onToggleGridView() {
        _isGridView.value = !_isGridView.value
    }

    fun renameDocument(docId: String, newTitle: String) {
        if (newTitle.isBlank()) return
        viewModelScope.launch {
            repository.renameDocument(docId, newTitle.trim())
            _events.emit(LibraryEvent.ShowMessage("Nama dokumen berhasil diubah"))
        }
    }

    fun duplicateDocument(docId: String) {
        viewModelScope.launch {
            _isDuplicating.value = true
            val result = repository.duplicateDocument(docId)
            _isDuplicating.value = false
            if (result.isSuccess) {
                _events.emit(LibraryEvent.ShowMessage("Dokumen berhasil diduplikasi"))
            } else {
                _events.emit(LibraryEvent.ShowError(result.exceptionOrNull()?.message ?: "Gagal menduplikasi"))
            }
        }
    }

    fun deleteDocumentWithUndo(
        doc: LibraryDocument,
        onUndoAvailable: (undoAction: () -> Unit) -> Unit
    ) {
        viewModelScope.launch {
            val deleted = repository.deleteDocument(doc.id)
            if (deleted != null) {
                // Schedule physical deletion in background after 4s timeout
                val purgeJob = repository.schedulePermanentDelete(deleted, delayMs = 4000L)

                onUndoAvailable {
                    viewModelScope.launch {
                        purgeJob.cancel()
                        repository.restoreDocument(deleted)
                    }
                }
            }
        }
    }

    fun shareDocument(context: Context, doc: LibraryDocument) {
        viewModelScope.launch {
            val pdfPath = doc.pdfPath
            if (pdfPath.isNullOrBlank()) {
                _events.emit(LibraryEvent.ShowError("File PDF tidak ditemukan"))
                return@launch
            }

            val uri = if (pdfPath.startsWith("content://")) {
                Uri.parse(pdfPath)
            } else {
                val file = File(pdfPath)
                if (!file.exists()) {
                    _events.emit(LibraryEvent.ShowError("File fisik tidak ditemukan"))
                    return@launch
                }
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }

            ShareHelper.sharePayload(
                context = context,
                payload = SharePayload(
                    uris = listOf(uri),
                    mimeType = "application/pdf",
                    title = doc.title,
                    isMultiple = false
                )
            )
        }
    }
}

sealed class LibraryEvent {
    data class ShowMessage(val message: String) : LibraryEvent()
    data class ShowError(val error: String) : LibraryEvent()
}
