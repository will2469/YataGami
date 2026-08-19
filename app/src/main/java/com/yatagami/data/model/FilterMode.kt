package com.yatagami.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class FilterMode {
    NONE, GRAYSCALE, BLACK_WHITE, MAGIC_COLOR, SHARPEN, AUTO
}
