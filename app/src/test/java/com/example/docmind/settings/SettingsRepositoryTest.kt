package com.example.docmind.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsRepositoryTest {
    @Test
    fun emptyStorageReturnsSystemThemeAndDefaultTextSize() {
        val repository = SettingsRepository(InMemorySettingsStorage())

        assertEquals(AppSettings(ThemeMode.SYSTEM, TextSize.DEFAULT), repository.load())
    }

    @Test
    fun invalidThemeFallsBackWithoutDiscardingValidTextSize() {
        val storage = InMemorySettingsStorage(
            mapOf("theme_mode" to "UNKNOWN", "text_size" to "EXTRA_LARGE")
        )

        assertEquals(
            AppSettings(ThemeMode.SYSTEM, TextSize.EXTRA_LARGE),
            SettingsRepository(storage).load()
        )
    }

    @Test
    fun invalidTextSizeFallsBackWithoutDiscardingValidTheme() {
        val storage = InMemorySettingsStorage(
            mapOf("theme_mode" to "DARK", "text_size" to "")
        )

        assertEquals(
            AppSettings(ThemeMode.DARK, TextSize.DEFAULT),
            SettingsRepository(storage).load()
        )
    }

    @Test
    fun missingValuesFallBackIndependently() {
        assertEquals(
            AppSettings(ThemeMode.LIGHT, TextSize.DEFAULT),
            SettingsRepository(InMemorySettingsStorage(mapOf("theme_mode" to "LIGHT"))).load()
        )
        assertEquals(
            AppSettings(ThemeMode.SYSTEM, TextSize.LARGE),
            SettingsRepository(InMemorySettingsStorage(mapOf("text_size" to "LARGE"))).load()
        )
    }

    @Test
    fun bothInvalidValuesReturnDefaults() {
        val storage = InMemorySettingsStorage(
            mapOf("theme_mode" to "dark", "text_size" to "1.15")
        )

        assertEquals(AppSettings(), SettingsRepository(storage).load())
    }

    @Test
    fun savedSettingsSurviveRepositoryRecreation() {
        val storage = InMemorySettingsStorage()
        val expected = AppSettings(ThemeMode.DARK, TextSize.EXTRA_LARGE)

        SettingsRepository(storage).save(expected)

        assertEquals(expected, SettingsRepository(storage).load())
        assertEquals("DARK", storage.read("theme_mode"))
        assertEquals("EXTRA_LARGE", storage.read("text_size"))
    }

    @Test
    fun everyThemeAndTextSizeCombinationRoundTrips() {
        val storage = InMemorySettingsStorage()

        for (theme in ThemeMode.values()) {
            for (textSize in TextSize.values()) {
                val expected = AppSettings(theme, textSize)
                SettingsRepository(storage).save(expected)

                assertEquals(
                    "Failed for theme=$theme and textSize=$textSize",
                    expected,
                    SettingsRepository(storage).load()
                )
            }
        }
    }

    @Test
    fun saveUpdatesOnlyOwnedKeys() {
        val storage = InMemorySettingsStorage(mapOf("unrelated" to "keep me"))

        SettingsRepository(storage).save(AppSettings(ThemeMode.LIGHT, TextSize.LARGE))

        assertEquals(
            mapOf("unrelated" to "keep me", "theme_mode" to "LIGHT", "text_size" to "LARGE"),
            storage.values
        )
    }

    @Test
    fun resetPersistsDefaultsAndPreservesUnrelatedData() {
        val storage = InMemorySettingsStorage(
            mapOf("theme_mode" to "DARK", "text_size" to "EXTRA_LARGE", "unrelated" to "keep me")
        )

        val resetSettings = SettingsRepository(storage).reset()

        assertEquals(AppSettings(), resetSettings)
        assertEquals(AppSettings(), SettingsRepository(storage).load())
        assertEquals(
            mapOf("theme_mode" to "SYSTEM", "text_size" to "DEFAULT", "unrelated" to "keep me"),
            storage.values
        )
    }

    @Test
    fun resetIsIdempotent() {
        val repository = SettingsRepository(InMemorySettingsStorage())

        assertEquals(AppSettings(), repository.reset())
        assertEquals(AppSettings(), repository.reset())
        assertEquals(AppSettings(), repository.load())
    }

    @Test
    fun textSizesExposeExpectedRelativeScales() {
        assertEquals(1f, TextSize.DEFAULT.scale, 0f)
        assertEquals(1.15f, TextSize.LARGE.scale, 0f)
        assertEquals(1.30f, TextSize.EXTRA_LARGE.scale, 0f)
    }

    private class InMemorySettingsStorage(
        initialValues: Map<String, String> = emptyMap()
    ) : SettingsStorage {
        val values = initialValues.toMutableMap()

        override fun read(key: String): String? = values[key]

        override fun write(values: Map<String, String>) {
            this.values.putAll(values)
        }
    }
}
