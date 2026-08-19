package com.yatagami

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.yatagami.ui.components.DraftRecoveryDialog
import com.yatagami.ui.components.NotificationPermissionHandler
import com.yatagami.ui.navigation.AppNavigation
import com.yatagami.ui.theme.YataGamiTheme
import com.yatagami.ui.viewmodel.LibraryViewModel
import com.yatagami.ui.viewmodel.ScanViewModel
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {

    private val scanViewModel: ScanViewModel by viewModels()
    private val libraryViewModel: LibraryViewModel by viewModels()

    private var activeNavController: NavController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleIncomingShareIntent(intent)

        setContent {
            YataGamiTheme {
                val navController = rememberNavController()
                activeNavController = navController

                NotificationPermissionHandler()

                AppNavigation(
                    scanViewModel = scanViewModel,
                    libraryViewModel = libraryViewModel,
                    navController = navController
                )

                // Draft Session Recovery Dialog (HiOS Kill Resilience)
                if (scanViewModel.showDraftDialog.value && scanViewModel.draftSession.value != null) {
                    val draft = scanViewModel.draftSession.value!!
                    DraftRecoveryDialog(
                        pageCount = draft.pages.size,
                        lastModified = draft.updatedAt,
                        onResume = {
                            scanViewModel.resumeDraftSession()
                            if (draft.pages.isNotEmpty()) {
                                navController.navigate("pages")
                            }
                        },
                        onDiscard = {
                            scanViewModel.discardDraftSession()
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingShareIntent(intent)
    }

    private fun handleIncomingShareIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val type = intent.type ?: return

        val importedDir = File(cacheDir, "imported").also { if (!it.exists()) it.mkdirs() }

        when (action) {
            Intent.ACTION_SEND -> {
                val streamUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }

                if (streamUri != null) {
                    if (type.startsWith("image/")) {
                        val cachedUri = copyUriToInternalCache(streamUri, importedDir, "jpg")
                        if (cachedUri != null) {
                            scanViewModel.clearPages()
                            scanViewModel.importImagesFromUris(this, listOf(cachedUri))
                            activeNavController?.navigate("pages")
                        }
                    } else if (type == "application/pdf") {
                        val cachedUri = copyUriToInternalCache(streamUri, importedDir, "pdf")
                        if (cachedUri != null) {
                            scanViewModel.clearPages()
                            scanViewModel.importPdfFromUri(this, cachedUri)
                            activeNavController?.navigate("pages")
                        }
                    }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val streamUris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                }

                if (!streamUris.isNullOrEmpty() && type.startsWith("image/")) {
                    val cachedUris = streamUris.mapNotNull { uri ->
                        copyUriToInternalCache(uri, importedDir, "jpg")
                    }
                    if (cachedUris.isNotEmpty()) {
                        scanViewModel.clearPages()
                        scanViewModel.importImagesFromUris(this, cachedUris)
                        activeNavController?.navigate("pages")
                    }
                }
            }
        }
    }

    private fun copyUriToInternalCache(sourceUri: Uri, targetDir: File, extension: String): Uri? {
        return try {
            val targetFile = File(targetDir, "import_${System.currentTimeMillis()}_${(0..999).random()}.$extension")
            contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            Uri.fromFile(targetFile)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            scanViewModel.forceSaveSession()
        }
    }
}
