package com.yatagami.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.yatagami.data.model.FilterMode

val filters = listOf(
    "Normal" to FilterMode.NONE,
    "Grayscale" to FilterMode.GRAYSCALE,
    "B&W" to FilterMode.BLACK_WHITE,
    "Magic Color" to FilterMode.MAGIC_COLOR,
    "Sharpen" to FilterMode.SHARPEN
)

@Composable
fun FilterScreen(pageId: String, navController: NavController, viewModel: com.yatagami.ui.viewmodel.ScanViewModel) {
    val page = viewModel.pages.find { it.id == pageId }
    page?.let { p ->
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Image(
                bitmap = p.getDisplayBitmap().asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
            LazyRow(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                items(filters) { (label, mode) ->
                    FilterChip(
                        selected = p.filterMode == mode,
                        onClick = { viewModel.updateFilter(pageId, mode) },
                        label = { Text(label) },
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
            Button(
                onClick = { navController.navigate("pages") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Selesai")
            }
        }
    }
}
