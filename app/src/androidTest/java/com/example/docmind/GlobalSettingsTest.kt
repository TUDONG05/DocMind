package com.example.docmind

import com.example.docmind.view.MainActivity

import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.docmind.model.settings.AppSettings
import com.example.docmind.model.settings.SettingsRepository
import com.example.docmind.model.settings.SharedPreferencesSettingsStorage
import com.example.docmind.model.settings.TextSize
import com.example.docmind.model.settings.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GlobalSettingsTest {
    @Test
    fun mainActivityAppliesSavedThemeTextSizeAndLanguage() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val repository = SettingsRepository(SharedPreferencesSettingsStorage(context))
        val originalSettings = repository.load()
        lateinit var originalLocales: LocaleListCompat
        instrumentation.runOnMainSync {
            originalLocales = if (android.os.Build.VERSION.SDK_INT >= 33) {
                LocaleListCompat.wrap(context.getSystemService(android.app.LocaleManager::class.java).applicationLocales)
            } else AppCompatDelegate.getApplicationLocales()
        }
        val systemFontScale = context.resources.configuration.fontScale

        try {
            for ((theme, language, textSize) in listOf(
                Triple(ThemeMode.DARK, "en", TextSize.EXTRA_LARGE),
                Triple(ThemeMode.LIGHT, "vi", TextSize.DEFAULT)
            )) {
                repository.save(AppSettings(theme, textSize))
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    scenario.onActivity {
                        AppCompatDelegate.setApplicationLocales(
                            LocaleListCompat.forLanguageTags(language)
                        )
                    }
                    instrumentation.waitForIdleSync()
                    scenario.recreate()
                    scenario.onActivity { activity ->
                        val configuration = activity.resources.configuration
                        assertEquals(language, configuration.locales[0].language)
                        assertEquals(
                            if (theme == ThemeMode.DARK) Configuration.UI_MODE_NIGHT_YES
                            else Configuration.UI_MODE_NIGHT_NO,
                            configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                        )
                        assertEquals(
                            systemFontScale * textSize.scale,
                            configuration.fontScale,
                            0.01f
                        )
                        assertEquals(
                            if (language == "en") "Documents" else "Tài liệu",
                            activity.getString(R.string.nav_documents)
                        )
                    }
                    // Recreation must not compound the user's font multiplier.
                    scenario.recreate()
                    scenario.onActivity { activity ->
                        assertEquals(
                            systemFontScale * textSize.scale,
                            activity.resources.configuration.fontScale,
                            0.01f
                        )
                    }
                }
            }
        } finally {
            repository.save(originalSettings)
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                context.getSystemService(android.app.LocaleManager::class.java)
                    .applicationLocales = originalLocales.unwrap() as android.os.LocaleList
            } else {
                instrumentation.runOnMainSync {
                    AppCompatDelegate.setApplicationLocales(originalLocales)
                }
            }
        }
    }
}
