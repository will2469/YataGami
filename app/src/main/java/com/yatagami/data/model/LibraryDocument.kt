package com.yatagami.data.model

import androidx.annotation.StringRes
import com.yatagami.R
import java.util.Calendar

enum class DateCategory(@StringRes val titleRes: Int) {
    TODAY(R.string.category_today),
    YESTERDAY(R.string.category_yesterday),
    THIS_WEEK(R.string.category_this_week),
    THIS_MONTH(R.string.category_this_month),
    OLDER(R.string.category_older);

    companion object {
        fun fromTimestamp(timestamp: Long): DateCategory {
            val now = Calendar.getInstance()
            val docTime = Calendar.getInstance().apply { timeInMillis = timestamp }

            val nowYear = now.get(Calendar.YEAR)
            val docYear = docTime.get(Calendar.YEAR)
            val nowDayOfYear = now.get(Calendar.DAY_OF_YEAR)
            val docDayOfYear = docTime.get(Calendar.DAY_OF_YEAR)

            if (nowYear == docYear) {
                if (nowDayOfYear == docDayOfYear) return TODAY
                if (nowDayOfYear - docDayOfYear == 1) return YESTERDAY
                if (nowDayOfYear - docDayOfYear in 2..7) return THIS_WEEK
                if (now.get(Calendar.MONTH) == docTime.get(Calendar.MONTH)) return THIS_MONTH
            }
            return OLDER
        }
    }
}

data class LibraryDocument(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pageCount: Int,
    val fileSizeBytes: Long,
    val primaryDocType: DocumentType = DocumentType.A4,
    val pdfPath: String? = null,
    val thumbnailPath: String = ""
) {
    val dateCategory: DateCategory
        get() = DateCategory.fromTimestamp(updatedAt)

    fun formattedFileSize(): String {
        if (fileSizeBytes <= 0) return "0 KB"
        val kb = fileSizeBytes / 1024.0
        val mb = kb / 1024.0
        return if (mb >= 1.0) {
            String.format(java.util.Locale.US, "%.1f MB", mb)
        } else {
            String.format(java.util.Locale.US, "%d KB", kb.toInt().coerceAtLeast(1))
        }
    }
}
