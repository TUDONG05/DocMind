package com.example.docmind.view.settings

import com.example.docmind.model.settings.AppSettings
import com.example.docmind.model.settings.SettingsRepository
import com.example.docmind.model.settings.SharedPreferencesSettingsStorage
import com.example.docmind.model.settings.ThemeMode

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate

/** Applies DocMind preferences without changing the device's global settings. */
abstract class SettingsAwareActivity : AppCompatActivity() {
    private lateinit var appliedSettings: AppSettings

    protected val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(SharedPreferencesSettingsStorage(this))
    }

    override fun attachBaseContext(newBase: Context) {
        appliedSettings = SettingsRepository(SharedPreferencesSettingsStorage(newBase)).load()
        val scale = appliedSettings.textSize.scale
        // newBase is unwrapped: scaling Activity resources would compound on recreation.
        val context = if (scale == 1f) {
            newBase
        } else {
            val configuration = Configuration(newBase.resources.configuration)
            configuration.fontScale *= scale
            newBase.createConfigurationContext(configuration)
        }
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        delegate.localNightMode = when (appliedSettings.theme) {
            ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
        }
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        val storedSettings = settingsRepository.load()
        if (storedSettings != appliedSettings) {
            appliedSettings = storedSettings
            recreate()
        }
    }
}
