package com.yatagami.data.repository

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.yatagami.data.model.ImageExportFormat
import com.yatagami.data.model.PdfCompressionTier
import com.yatagami.data.model.ScannedPage
import com.yatagami.service.PdfProcessingService
import com.yatagami.utils.DevicePerformanceMonitor
import com.yatagami.utils.ShareHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ScanRepository(private val context: Context) {

    init {
        PDFBoxResourceLoader.init(context)
    }

    fun generateDefaultDocumentTitle(pages: List<ScannedPage>): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val timestamp = dateFormat.format(Date())
        val docType = pages.firstOrNull()?.docType?.name ?: "DOC"
        return "Scan_${timestamp}_$docType"
    }

    fun sanitizeFileName(name: String): String {
        val sanitized = name.trim().replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        return sanitized.ifEmpty { "Scan_Document" }
    }

    suspend fun cacheOriginalCapture(page: ScannedPage): String? = withContext(Dispatchers.IO) {
        try {
            val cacheDir = File(context.cacheDir, "scan_originals")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val file = File(cacheDir, "${page.id}_raw.jpg")
            FileOutputStream(file).use { out ->
                page.originalBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            page.cacheFilePath = file.absolutePath
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun buildPdfDocumentStream(
        pages: List<ScannedPage>,
        title: String,
        author: String = "YataGami",
        compressionTier: PdfCompressionTier = PdfCompressionTier.STANDARD,
        outputStream: OutputStream
    ) {
        val document = PDDocument()
        try {
            // 1. PDF Metadata Intelligence
            val info = PDDocumentInformation()
            info.title = title
            info.author = author
            info.creator = "YataGami App"
            info.producer = "YataGami Document Engine"
            info.subject = "Scanned Document"
            val now = Calendar.getInstance()
            info.creationDate = now
            info.modificationDate = now
            document.documentInformation = info

            // 2. Determine Max Dimension based on DPI tier
            val maxDimension = when (compressionTier) {
                PdfCompressionTier.MINIMUM -> 1200
                PdfCompressionTier.STANDARD -> 1800
                PdfCompressionTier.HIGH_QUALITY -> 3508
            }

            val sortedPages = pages.sortedBy { it.pageNumber }
            for ((index, page) in sortedPages.withIndex()) {
                PdfProcessingService.updateProgress(
                    context,
                    "Menyusun PDF... (${index + 1}/${sortedPages.size} halaman)"
                )

                val displayBitmap = page.getDisplayBitmap()
                val originalW = displayBitmap.width
                val originalH = displayBitmap.height
                val maxSide = maxOf(originalW, originalH)

                // Downscale adaptif per halaman
                val scaleFactor = if (maxSide > maxDimension) {
                    maxDimension.toFloat() / maxSide
                } else 1.0f

                val processedBmp = if (scaleFactor < 1.0f) {
                    Bitmap.createScaledBitmap(
                        displayBitmap,
                        (originalW * scaleFactor).toInt().coerceAtLeast(1),
                        (originalH * scaleFactor).toInt().coerceAtLeast(1),
                        true
                    )
                } else {
                    displayBitmap
                }

                val widthPt = PDRectangle.A4.width
                val heightPt = PDRectangle.A4.height
                val pageRect = PDRectangle(widthPt, heightPt)
                val pdPage = PDPage(pageRect)
                document.addPage(pdPage)

                val pdImage = JPEGFactory.createFromImage(
                    document,
                    processedBmp,
                    compressionTier.jpegQuality / 100f
                )

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

                // Recycle downscaled bitmap immediately to protect heap RAM
                if (processedBmp != displayBitmap && !processedBmp.isRecycled) {
                    processedBmp.recycle()
                }

                if (DevicePerformanceMonitor.isUnderMemoryPressure()) {
                    System.gc()
                }
            }

            document.save(outputStream)
        } finally {
            document.close()
        }
    }

    suspend fun savePdf(
        pages: List<ScannedPage>,
        title: String,
        author: String = "YataGami",
        compressionTier: PdfCompressionTier = PdfCompressionTier.STANDARD
    ): Result<String> = withContext(Dispatchers.IO) {
        if (pages.isEmpty()) return@withContext Result.failure(IllegalArgumentException("No pages to export"))

        try {
            PdfProcessingService.start(context, "Menyusun PDF... (0/${pages.size} halaman)")

            val safeTitle = sanitizeFileName(title)
            val fileName = "$safeTitle.pdf"

            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/YataGami")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val fileUri = context.contentResolver.insert(collection, values)
                    ?: return@withContext Result.failure(Exception("Failed to create MediaStore entry"))

                context.contentResolver.openOutputStream(fileUri)?.use { out ->
                    buildPdfDocumentStream(pages, safeTitle, author, compressionTier, out)
                }

                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(fileUri, values, null, null)
                fileUri.toString()
            } else {
                @Suppress("DEPRECATION")
                val docsDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    "YataGami"
                )
                if (!docsDir.exists()) docsDir.mkdirs()
                val file = File(docsDir, fileName)
                FileOutputStream(file).use { out ->
                    buildPdfDocumentStream(pages, safeTitle, author, compressionTier, out)
                }
                file.absolutePath
            }

            Result.success(uri)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            PdfProcessingService.stop(context)
        }
    }

    suspend fun createTempPdfForShare(
        pages: List<ScannedPage>,
        title: String,
        compressionTier: PdfCompressionTier = PdfCompressionTier.STANDARD
    ): Result<Uri> = withContext(Dispatchers.IO) {
        if (pages.isEmpty()) return@withContext Result.failure(IllegalArgumentException("No pages to share"))

        try {
            PdfProcessingService.start(context, "Menyiapkan PDF untuk dibagikan...")
            ShareHelper.cleanShareCache(context)

            val shareDir = File(context.cacheDir, "share")
            if (!shareDir.exists()) shareDir.mkdirs()

            val safeTitle = sanitizeFileName(title)
            val file = File(shareDir, "$safeTitle.pdf")

            FileOutputStream(file).use { out ->
                buildPdfDocumentStream(pages, safeTitle, "YataGami", compressionTier, out)
            }

            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)
            Result.success(uri)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            PdfProcessingService.stop(context)
        }
    }

    suspend fun saveSingleImageToGallery(
        page: ScannedPage,
        format: ImageExportFormat = ImageExportFormat.JPG_90,
        customTitle: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val bitmap = page.getDisplayBitmap()
            val baseTitle = customTitle?.let { sanitizeFileName(it) } ?: "SCAN_${System.currentTimeMillis()}"
            val fileName = "${baseTitle}_page_${page.pageNumber}.${format.extension}"

            val uriString = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, format.mimeType)
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/YataGami")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val fileUri = context.contentResolver.insert(collection, values)
                    ?: return@withContext Result.failure(Exception("Failed to insert MediaStore image entry"))

                context.contentResolver.openOutputStream(fileUri)?.use { out ->
                    val compressFormat = if (format == ImageExportFormat.PNG_LOSSLESS) {
                        Bitmap.CompressFormat.PNG
                    } else {
                        Bitmap.CompressFormat.JPEG
                    }
                    bitmap.compress(compressFormat, format.quality, out)
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
                    val compressFormat = if (format == ImageExportFormat.PNG_LOSSLESS) {
                        Bitmap.CompressFormat.PNG
                    } else {
                        Bitmap.CompressFormat.JPEG
                    }
                    bitmap.compress(compressFormat, format.quality, out)
                }
                file.absolutePath
            }

            Result.success(uriString)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveImagesToGallery(
        pages: List<ScannedPage>,
        format: ImageExportFormat = ImageExportFormat.JPG_90,
        customTitle: String? = null
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val savedUris = mutableListOf<String>()
            for (page in pages.sortedBy { it.pageNumber }) {
                val singleResult = saveSingleImageToGallery(page, format, customTitle)
                if (singleResult.isSuccess) {
                    savedUris.add(singleResult.getOrThrow())
                }
            }
            Result.success(savedUris)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createTempImagesForShare(
        pages: List<ScannedPage>,
        title: String,
        format: ImageExportFormat = ImageExportFormat.JPG_90
    ): Result<List<Uri>> = withContext(Dispatchers.IO) {
        if (pages.isEmpty()) return@withContext Result.failure(IllegalArgumentException("No images to share"))

        try {
            ShareHelper.cleanShareCache(context)

            val shareDir = File(context.cacheDir, "share")
            if (!shareDir.exists()) shareDir.mkdirs()

            val safeTitle = sanitizeFileName(title)
            val authority = "${context.packageName}.fileprovider"
            val uriList = mutableListOf<Uri>()

            for (page in pages.sortedBy { it.pageNumber }) {
                val file = File(shareDir, "${safeTitle}_page_${page.pageNumber}.${format.extension}")
                FileOutputStream(file).use { out ->
                    val bitmap = page.getDisplayBitmap()
                    val compressFormat = if (format == ImageExportFormat.PNG_LOSSLESS) {
                        Bitmap.CompressFormat.PNG
                    } else {
                        Bitmap.CompressFormat.JPEG
                    }
                    bitmap.compress(compressFormat, format.quality, out)
                }
                val uri = FileProvider.getUriForFile(context, authority, file)
                uriList.add(uri)
            }

            Result.success(uriList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
