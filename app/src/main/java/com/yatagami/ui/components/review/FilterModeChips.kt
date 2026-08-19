package com.yatagami.ui.components.review

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yatagami.data.model.FilterMode

val reviewFilterOptions = listOf(
    "Auto" to FilterMode.AUTO,
    "Normal" to FilterMode.NONE,
    "Magic Color" to FilterMode.MAGIC_COLOR,
    "B&W" to FilterMode.BLACK_WHITE,
    "Grayscale" to FilterMode.GRAYSCALE,
    "Sharpen" to FilterMode.SHARPEN
)

@Composable
fun FilterModeChips(
    currentMode: FilterMode,
    onFilterSelected: (FilterMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        reviewFilterOptions.forEach { (label, mode) ->
            FilterChip(
                selected = currentMode == mode,
                onClick = { onFilterSelected(mode) },
                label = { Text(label, fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF00E676),
                    selectedLabelColor = Color.Black,
                    containerColor = Color(0xFF2C2C2C),
                    labelColor = Color.White
                )
            )
        }
    }
}
