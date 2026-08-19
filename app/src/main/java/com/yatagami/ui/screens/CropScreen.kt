package com.yatagami.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun CropScreen(pageId: String, navController: NavController, viewModel: com.yatagami.ui.viewmodel.ScanViewModel) {
    val page = viewModel.pages.find { it.id == pageId }
    page?.let {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Image(
                bitmap = it.getDisplayBitmap().asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
            Button(
                onClick = { navController.navigate("filter/$pageId") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Text("Lanjut ke Filter")
            }
        }
    }
}
