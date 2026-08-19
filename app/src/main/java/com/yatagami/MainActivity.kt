package com.yatagami

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yatagami.ui.screens.CameraScreen
import com.yatagami.ui.screens.CropScreen
import com.yatagami.ui.screens.FilterScreen
import com.yatagami.ui.screens.PageListScreen
import com.yatagami.ui.theme.YataGamiTheme
import com.yatagami.ui.viewmodel.ScanViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: ScanViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            YataGamiTheme {
                AppNavigation(viewModel)
            }
        }
    }
}

@Composable
fun AppNavigation(viewModel: ScanViewModel = viewModel()) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "camera") {
        composable("camera") { CameraScreen(navController, viewModel) }
        composable("pages") { PageListScreen(navController, viewModel) }
        composable(
            "crop/{pageId}",
            arguments = listOf(navArgument("pageId") { type = NavType.StringType })
        ) { backStackEntry ->
            val pageId = backStackEntry.arguments?.getString("pageId") ?: ""
            CropScreen(pageId, navController, viewModel)
        }
        composable(
            "filter/{pageId}",
            arguments = listOf(navArgument("pageId") { type = NavType.StringType })
        ) { backStackEntry ->
            val pageId = backStackEntry.arguments?.getString("pageId") ?: ""
            FilterScreen(pageId, navController, viewModel)
        }
    }
}
