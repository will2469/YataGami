package com.yatagami

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.navigation.compose.rememberNavController
import com.yatagami.ui.components.DraftRecoveryDialog
import com.yatagami.ui.components.NotificationPermissionHandler
import com.yatagami.ui.navigation.AppNavigation
import com.yatagami.ui.theme.YataGamiTheme
import com.yatagami.ui.viewmodel.ScanViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: ScanViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            YataGamiTheme {
                val navController = rememberNavController()

                NotificationPermissionHandler()

                AppNavigation(
                    viewModel = viewModel,
                    navController = navController
                )

                // Draft Session Recovery Dialog (HiOS Kill Resilience)
                if (viewModel.showDraftDialog.value && viewModel.draftSession.value != null) {
                    val draft = viewModel.draftSession.value!!
                    DraftRecoveryDialog(
                        pageCount = draft.pages.size,
                        lastModified = draft.updatedAt,
                        onResume = {
                            viewModel.resumeDraftSession()
                            if (draft.pages.isNotEmpty()) {
                                navController.navigate("pages")
                            }
                        },
                        onDiscard = {
                            viewModel.discardDraftSession()
                        }
                    )
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Save session state to disk if app is backgrounded (skip if rotating/config change)
        if (!isChangingConfigurations) {
            viewModel.forceSaveSession()
        }
    }
}
