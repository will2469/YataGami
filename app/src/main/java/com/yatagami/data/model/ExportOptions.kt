package com.yatagami.data.model

import android.net.Uri
import androidx.annotation.StringRes
import com.yatagami.R

enum class PdfCompressionTier(
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int,
    val targetDpi: Int,
    val jpegQuality: Int
) {
    MINIMUM(
        titleRes = R.string.export_compression_min,
        descRes = R.string.export_compression_min_desc,
        targetDpi = 100,
        jpegQuality = 65
    ),
    STANDARD(
        titleRes = R.string.export_compression_std,
        descRes = R.string.export_compression_std_desc,
        targetDpi = 150,
        jpegQuality = 85
    ),
    HIGH_QUALITY(
        titleRes = R.string.export_compression_high,
        descRes = R.string.export_compression_high_desc,
        targetDpi = 300,
        jpegQuality = 95
    )
}

enum class ImageExportFormat(
    @StringRes val titleRes: Int,
    val quality: Int,
    val extension: String,
    val mimeType: String
) {
    JPG_80(
        titleRes = R.string.export_format_jpg_80,
        quality = 80,
        extension = "jpg",
        mimeType = "image/jpeg"
    ),
    JPG_90(
        titleRes = R.string.export_format_jpg_90,
        quality = 90,
        extension = "jpg",
        mimeType = "image/jpeg"
    ),
    JPG_100(
        titleRes = R.string.export_format_jpg_100,
        quality = 100,
        extension = "jpg",
        mimeType = "image/jpeg"
    ),
    PNG_LOSSLESS(
        titleRes = R.string.export_format_png,
        quality = 100,
        extension = "png",
        mimeType = "image/png"
    )
}

data class SharePayload(
    val uris: List<Uri>,
    val mimeType: String,
    val title: String,
    val isMultiple: Boolean
)
