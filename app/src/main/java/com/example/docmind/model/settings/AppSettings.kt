package com.example.docmind.model.settings

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class TextSize(val scale: Float) {
    DEFAULT(1f),
    LARGE(1.15f),
    EXTRA_LARGE(1.30f)
}

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val textSize: TextSize = TextSize.DEFAULT
)
