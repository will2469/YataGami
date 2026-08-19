package com.yatagami.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.yatagami.data.model.FilterMode
import com.yatagami.opencv.ImageProcessor

val filters = listOf(
    "Auto" to FilterMode.AUTO,
    "Normal" to FilterMode.NONE,
    "Grayscale" to FilterMode.GRAYSCALE,
    "B&W" to FilterMode.BLACK_WHITE,
    "Magic Color" to FilterMode.MAGIC_COLOR,
    "Sharpen" to FilterMode.SHARPEN
)

@Composable
fun FilterScreen(pageId: String, navController: NavController, viewModel: com.yatagami.ui.viewmodel.ScanViewModel) {
    val page = viewModel.pages.find { it.id == pageId }
    val processor = remember { ImageProcessor() }
    var blurWarning by remember { mutableStateOf(false) }
    var glareWarning by remember { mutableStateOf(false) }

    page?.let { p ->
        LaunchedEffect(p.id) {
            val bitmap = p.croppedBitmap ?: p.originalBitmap
            val blurScore = processor.calculateBlurScore(bitmap)
            val glareRatio = processor.calculateGlareRatio(bitmap)
            blurWarning = blurScore < 85.0f
            glareWarning = glareRatio > 0.08f
        }

        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Image(
                    bitmap = p.getDisplayBitmap().asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )

                // Smart Quality Warning Badge
                if (blurWarning || glareWarning) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(8.dp)
                            .background(Color(0xCCB00020), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (blurWarning && glareWarning) "⚠️ Dokumen agak buram & terdapat pantulan silau"
                            else if (blurWarning) "⚠️ Dokumen agak buram, pertimbangkan foto ulang"
                            else "⚠️ Terdeteksi pantulan cahaya / silau di dokumen",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                items(filters) { (label, mode) ->
                    FilterChip(
                        selected = p.filterMode == mode,
                        onClick = { viewModel.updateFilter(pageId, mode) },
                        label = { Text(label) },
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { navController.navigate("pages") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Selesai")
            }
        }
    }
}
