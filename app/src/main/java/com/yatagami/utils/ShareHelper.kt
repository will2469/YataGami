package com.yatagami.utils

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.yatagami.R
import com.yatagami.data.model.SharePayload
import java.io.File

object ShareHelper {

    fun sharePayload(context: Context, payload: SharePayload) {
        if (payload.uris.isEmpty()) return

        val intent = if (!payload.isMultiple && payload.uris.size == 1) {
            val singleUri = payload.uris.first()
            Intent(Intent.ACTION_SEND).apply {
                type = payload.mimeType
                putExtra(Intent.EXTRA_STREAM, singleUri)
                clipData = ClipData.newRawUri(payload.title, singleUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            val arrayUris = ArrayList(payload.uris)
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = payload.mimeType
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayUris)
                
                // Explicit ClipData for multi-share permission propagation
                val clipDescription = ClipDescription(payload.title, arrayOf(payload.mimeType))
                val clipData = ClipData(clipDescription, ClipData.Item(payload.uris.first()))
                payload.uris.drop(1).forEach { uri ->
                    clipData.addItem(ClipData.Item(uri))
                }
                this.clipData = clipData
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

        val chooserTitle = context.getString(R.string.export_share_chooser_title)
        val chooser = Intent.createChooser(intent, chooserTitle).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        try {
            context.startActivity(chooser)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.export_share_no_app, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun cleanShareCache(context: Context) {
        try {
            val shareDir = File(context.cacheDir, "share")
            if (shareDir.exists() && shareDir.isDirectory) {
                shareDir.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
