package com.yatagami.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.yatagami.data.model.DocumentType
import com.yatagami.data.model.FilterMode
import com.yatagami.data.model.LibraryDocument
import com.yatagami.data.session.PageSessionData

class DocumentLibraryDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    companion object {
        const val DATABASE_NAME = "yatagami_library.db"
        const val DATABASE_VERSION = 1

        const val TABLE_DOCUMENTS = "documents"
        const val COL_DOC_ID = "id"
        const val COL_DOC_TITLE = "title"
        const val COL_DOC_CREATED_AT = "created_at"
        const val COL_DOC_UPDATED_AT = "updated_at"
        const val COL_DOC_PAGE_COUNT = "page_count"
        const val COL_DOC_FILE_SIZE = "file_size_bytes"
        const val COL_DOC_TYPE = "primary_doc_type"
        const val COL_DOC_PDF_PATH = "pdf_path"
        const val COL_DOC_THUMBNAIL_PATH = "thumbnail_path"

        const val TABLE_PAGES = "document_pages"
        const val COL_PAGE_ID = "page_id"
        const val COL_PAGE_DOC_ID = "document_id"
        const val COL_PAGE_NUMBER = "page_number"
        const val COL_PAGE_DOC_TYPE = "doc_type"
        const val COL_PAGE_IS_PORTRAIT = "is_portrait"
        const val COL_PAGE_ORIENTATION = "orientation_degrees"
        const val COL_PAGE_FILTER = "filter_mode"
        const val COL_PAGE_RAW_PATH = "raw_path"
        const val COL_PAGE_WARPED_PATH = "warped_path"
        const val COL_PAGE_PROCESSED_PATH = "processed_path"

        @Volatile
        private var INSTANCE: DocumentLibraryDatabase? = null

        fun getInstance(context: Context): DocumentLibraryDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DocumentLibraryDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.enableWriteAheadLogging()
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_DOCUMENTS (
                $COL_DOC_ID TEXT PRIMARY KEY,
                $COL_DOC_TITLE TEXT NOT NULL,
                $COL_DOC_CREATED_AT INTEGER NOT NULL,
                $COL_DOC_UPDATED_AT INTEGER NOT NULL,
                $COL_DOC_PAGE_COUNT INTEGER NOT NULL,
                $COL_DOC_FILE_SIZE INTEGER NOT NULL,
                $COL_DOC_TYPE TEXT NOT NULL,
                $COL_DOC_PDF_PATH TEXT,
                $COL_DOC_THUMBNAIL_PATH TEXT NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_PAGES (
                $COL_PAGE_ID TEXT PRIMARY KEY,
                $COL_PAGE_DOC_ID TEXT NOT NULL REFERENCES $TABLE_DOCUMENTS($COL_DOC_ID) ON DELETE CASCADE,
                $COL_PAGE_NUMBER INTEGER NOT NULL,
                $COL_PAGE_DOC_TYPE TEXT NOT NULL,
                $COL_PAGE_IS_PORTRAIT INTEGER NOT NULL,
                $COL_PAGE_ORIENTATION INTEGER NOT NULL,
                $COL_PAGE_FILTER TEXT NOT NULL,
                $COL_PAGE_RAW_PATH TEXT NOT NULL,
                $COL_PAGE_WARPED_PATH TEXT,
                $COL_PAGE_PROCESSED_PATH TEXT
            )
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_docs_updated ON $TABLE_DOCUMENTS($COL_DOC_UPDATED_AT DESC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_docs_title ON $TABLE_DOCUMENTS($COL_DOC_TITLE)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Migration logic for future schema versions
    }

    // Query all document metadata sorted by updated_at descending
    fun getAllDocuments(): List<LibraryDocument> {
        val list = mutableListOf<LibraryDocument>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_DOCUMENTS,
            null,
            null,
            null,
            null,
            null,
            "$COL_DOC_UPDATED_AT DESC"
        )
        cursor.use { c ->
            val idIdx = c.getColumnIndexOrThrow(COL_DOC_ID)
            val titleIdx = c.getColumnIndexOrThrow(COL_DOC_TITLE)
            val createdIdx = c.getColumnIndexOrThrow(COL_DOC_CREATED_AT)
            val updatedIdx = c.getColumnIndexOrThrow(COL_DOC_UPDATED_AT)
            val countIdx = c.getColumnIndexOrThrow(COL_DOC_PAGE_COUNT)
            val sizeIdx = c.getColumnIndexOrThrow(COL_DOC_FILE_SIZE)
            val typeIdx = c.getColumnIndexOrThrow(COL_DOC_TYPE)
            val pdfIdx = c.getColumnIndexOrThrow(COL_DOC_PDF_PATH)
            val thumbIdx = c.getColumnIndexOrThrow(COL_DOC_THUMBNAIL_PATH)

            while (c.moveToNext()) {
                val typeName = c.getString(typeIdx)
                val docType = runCatching { DocumentType.valueOf(typeName) }.getOrDefault(DocumentType.A4)
                list.add(
                    LibraryDocument(
                        id = c.getString(idIdx),
                        title = c.getString(titleIdx),
                        createdAt = c.getLong(createdIdx),
                        updatedAt = c.getLong(updatedIdx),
                        pageCount = c.getInt(countIdx),
                        fileSizeBytes = c.getLong(sizeIdx),
                        primaryDocType = docType,
                        pdfPath = c.getString(pdfIdx),
                        thumbnailPath = c.getString(thumbIdx)
                    )
                )
            }
        }
        return list
    }

    fun getDocumentById(id: String): LibraryDocument? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_DOCUMENTS,
            null,
            "$COL_DOC_ID = ?",
            arrayOf(id),
            null,
            null,
            null
        )
        cursor.use { c ->
            if (c.moveToNext()) {
                val typeName = c.getString(c.getColumnIndexOrThrow(COL_DOC_TYPE))
                val docType = runCatching { DocumentType.valueOf(typeName) }.getOrDefault(DocumentType.A4)
                return LibraryDocument(
                    id = c.getString(c.getColumnIndexOrThrow(COL_DOC_ID)),
                    title = c.getString(c.getColumnIndexOrThrow(COL_DOC_TITLE)),
                    createdAt = c.getLong(c.getColumnIndexOrThrow(COL_DOC_CREATED_AT)),
                    updatedAt = c.getLong(c.getColumnIndexOrThrow(COL_DOC_UPDATED_AT)),
                    pageCount = c.getInt(c.getColumnIndexOrThrow(COL_DOC_PAGE_COUNT)),
                    fileSizeBytes = c.getLong(c.getColumnIndexOrThrow(COL_DOC_FILE_SIZE)),
                    primaryDocType = docType,
                    pdfPath = c.getString(c.getColumnIndexOrThrow(COL_DOC_PDF_PATH)),
                    thumbnailPath = c.getString(c.getColumnIndexOrThrow(COL_DOC_THUMBNAIL_PATH))
                )
            }
        }
        return null
    }

    fun insertOrUpdateDocument(doc: LibraryDocument, pages: List<PageSessionData> = emptyList()) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val docValues = ContentValues().apply {
                put(COL_DOC_ID, doc.id)
                put(COL_DOC_TITLE, doc.title)
                put(COL_DOC_CREATED_AT, doc.createdAt)
                put(COL_DOC_UPDATED_AT, doc.updatedAt)
                put(COL_DOC_PAGE_COUNT, doc.pageCount)
                put(COL_DOC_FILE_SIZE, doc.fileSizeBytes)
                put(COL_DOC_TYPE, doc.primaryDocType.name)
                put(COL_DOC_PDF_PATH, doc.pdfPath)
                put(COL_DOC_THUMBNAIL_PATH, doc.thumbnailPath)
            }
            db.insertWithOnConflict(TABLE_DOCUMENTS, null, docValues, SQLiteDatabase.CONFLICT_REPLACE)

            if (pages.isNotEmpty()) {
                db.delete(TABLE_PAGES, "$COL_PAGE_DOC_ID = ?", arrayOf(doc.id))
                for (page in pages) {
                    val pageValues = ContentValues().apply {
                        put(COL_PAGE_ID, page.pageId)
                        put(COL_PAGE_DOC_ID, doc.id)
                        put(COL_PAGE_NUMBER, page.pageNumber)
                        put(COL_PAGE_DOC_TYPE, page.docType.name)
                        put(COL_PAGE_IS_PORTRAIT, if (page.isPortrait) 1 else 0)
                        put(COL_PAGE_ORIENTATION, page.orientationDegrees)
                        put(COL_PAGE_FILTER, page.filterMode.name)
                        put(COL_PAGE_RAW_PATH, page.rawPath)
                        put(COL_PAGE_WARPED_PATH, page.warpedPath)
                        put(COL_PAGE_PROCESSED_PATH, page.processedPath)
                    }
                    db.insert(TABLE_PAGES, null, pageValues)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun updateDocumentTitle(id: String, newTitle: String, updatedAt: Long = System.currentTimeMillis()) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_DOC_TITLE, newTitle)
            put(COL_DOC_UPDATED_AT, updatedAt)
        }
        db.update(TABLE_DOCUMENTS, values, "$COL_DOC_ID = ?", arrayOf(id))
    }

    fun deleteDocument(id: String) {
        val db = writableDatabase
        db.delete(TABLE_DOCUMENTS, "$COL_DOC_ID = ?", arrayOf(id))
    }

    fun getPagesForDocument(docId: String): List<PageSessionData> {
        val list = mutableListOf<PageSessionData>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_PAGES,
            null,
            "$COL_PAGE_DOC_ID = ?",
            arrayOf(docId),
            null,
            null,
            "$COL_PAGE_NUMBER ASC"
        )
        cursor.use { c ->
            val pageIdIdx = c.getColumnIndexOrThrow(COL_PAGE_ID)
            val numIdx = c.getColumnIndexOrThrow(COL_PAGE_NUMBER)
            val typeIdx = c.getColumnIndexOrThrow(COL_PAGE_DOC_TYPE)
            val portraitIdx = c.getColumnIndexOrThrow(COL_PAGE_IS_PORTRAIT)
            val orientIdx = c.getColumnIndexOrThrow(COL_PAGE_ORIENTATION)
            val filterIdx = c.getColumnIndexOrThrow(COL_PAGE_FILTER)
            val rawIdx = c.getColumnIndexOrThrow(COL_PAGE_RAW_PATH)
            val warpedIdx = c.getColumnIndexOrThrow(COL_PAGE_WARPED_PATH)
            val processedIdx = c.getColumnIndexOrThrow(COL_PAGE_PROCESSED_PATH)

            while (c.moveToNext()) {
                val docType = runCatching { DocumentType.valueOf(c.getString(typeIdx)) }.getOrDefault(DocumentType.A4)
                val filterMode = runCatching { FilterMode.valueOf(c.getString(filterIdx)) }.getOrDefault(FilterMode.AUTO)
                list.add(
                    PageSessionData(
                        pageId = c.getString(pageIdIdx),
                        pageNumber = c.getInt(numIdx),
                        docType = docType,
                        isPortrait = c.getInt(portraitIdx) == 1,
                        orientationDegrees = c.getInt(orientIdx),
                        filterMode = filterMode,
                        rawPath = c.getString(rawIdx),
                        warpedPath = c.getString(warpedIdx),
                        processedPath = c.getString(processedIdx)
                    )
                )
            }
        }
        return list
    }
}
