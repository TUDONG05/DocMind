package com.example.docmind.model.settings

interface SettingsStorage {
    fun read(key: String): String?
    fun write(values: Map<String, String>)
}

class SettingsRepository(private val storage: SettingsStorage) {
    fun load(): AppSettings {
        val theme = storage.read(THEME_KEY)
        val textSize = storage.read(TEXT_SIZE_KEY)
        return AppSettings(
            theme = ThemeMode.entries.firstOrNull { it.name == theme } ?: ThemeMode.SYSTEM,
            textSize = TextSize.entries.firstOrNull { it.name == textSize } ?: TextSize.DEFAULT
        )
    }

    fun save(settings: AppSettings) {
        storage.write(mapOf(THEME_KEY to settings.theme.name, TEXT_SIZE_KEY to settings.textSize.name))
    }

    fun reset(): AppSettings = AppSettings().also(::save)

    private companion object {
        const val THEME_KEY = "theme_mode"
        const val TEXT_SIZE_KEY = "text_size"
    }
}
