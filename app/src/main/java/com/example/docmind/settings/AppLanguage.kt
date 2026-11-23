package com.example.docmind.settings

import java.util.Locale

/** Language state is persisted by AppCompat/the platform, separately from display settings. */
enum class AppLanguage(val languageTag: String) {
    SYSTEM(""),
    VIETNAMESE("vi"),
    ENGLISH("en");

    companion object {
        fun fromLanguageTags(tags: String): AppLanguage = when (
            tags.substringBefore(',').trim().substringBefore('-').lowercase(Locale.ROOT)
        ) {
            "vi" -> VIETNAMESE
            "en" -> ENGLISH
            else -> SYSTEM
        }
    }
}
