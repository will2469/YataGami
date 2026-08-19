package com.yatagami.data.repository

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.graphics.Bitmap
import com.yatagami.data.model.ScannedPage
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar

class ScanRepository(private val context: Context) {

    init {
        PDFBoxResourceLoader.init(context)
    }

    suspend fun saveImagesToGallery(
        pages: List<ScannedPage>,
        quality: Int = 90
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val savedUris = mutableListOf<String>()
            for (page in pages.sortedBy { it.pageNumber }) {
                val singleResult = saveSingleImageToGallery(page, quality)
                if (singleResult.isSuccess) {
                    savedUris.add(singleResult.getOrThrow())
                }
            }
            Result.success(savedUris)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveSingleImageToGallery(
        page: ScannedPage,
        quality: Int = 90
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val bitmap = page.getDisplayBitmap()
            val fileName = "SCAN_${System.currentTimeMillis()}_page_${page.pageNumber}.jpg"

            val uriString = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/YataGami")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val fileUri = context.contentResolver.insert(collection, values)
                    ?: return@withContext Result.failure(Exception("Failed to insert MediaStore image entry"))

                context.contentResolver.openOutputStream(fileUri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                }

                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(fileUri, values, null, null)
                fileUri.toString()
            } else {
                @Suppress("DEPRECATION")
                val picturesDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "YataGami"
                )
                if (!picturesDir.exists()) picturesDir.mkdirs()
                val file = File(picturesDir, fileName)
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                }
                file.absolutePath
            }

            Result.success(uriString)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun savePdf(
        pages: List<ScannedPage>,
        title: String,
        author: String = "",
        quality: Int = 85
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val document = PDDocument()
            val info = PDDocumentInformation()
            info.title = title
            info.author = author
            info.creationDate = Calendar.getInstance()
            document.documentInformation = info

            for (page in pages.sortedBy { it.pageNumber }) {
                val bitmap = page.getDisplayBitmap()
                val widthPt = PDRectangle.A4.width
                val heightPt = PDRectangle.A4.height
                val pageRect = PDRectangle(widthPt, heightPt)
                val pdPage = PDPage(pageRect)
                document.addPage(pdPage)

                val pdImage = JPEGFactory.createFromImage(document, bitmap, quality / 100f)

                PDPageContentStream(document, pdPage).use { stream ->
                    val imgWidth = pdImage.width.toFloat()
                    val imgHeight = pdImage.height.toFloat()
                    val ratio = minOf(widthPt / imgWidth, heightPt / imgHeight)
                    val drawWidth = imgWidth * ratio
                    val drawHeight = imgHeight * ratio
                    val x = (widthPt - drawWidth) / 2f
                    val y = (heightPt - drawHeight) / 2f
                    stream.drawImage(pdImage, x, y, drawWidth, drawHeight)
                }
            }

            val safeTitle = title.replace(Regex("[^a-zA-Z0-9\\\\u0000-.]"), "_")
            val fileName = "${safeTitle}_${System.currentTimeMillis()}.pdf"

            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS)
                }
                val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val fileUri = context.contentResolver.insert(collection, values)
                    ?: return@withContext Result.failure(Exception("Failed to create MediaStore entry"))
                context.contentResolver.openOutputStream(fileUri)?.use { out ->
                    document.save(out)
                }
                fileUri.toString()
            } else {
                @Suppress("DEPRECATION")
                val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                if (!docsDir.exists()) docsDir.mkdirs()
                val file = File(docsDir, fileName)
                FileOutputStream(file).use { out -> document.save(out) }
                file.absolutePath
            }

            document.close()
            Result.success(uri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
