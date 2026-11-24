package com.example.docmind.view.settings

import com.example.docmind.model.settings.AppLanguage
import com.example.docmind.model.settings.TextSize
import com.example.docmind.model.settings.ThemeMode

import android.content.pm.PackageManager
import android.os.Bundle
import android.text.format.Formatter
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.widget.NestedScrollView
import com.example.docmind.R
import com.example.docmind.viewmodel.StorageUiState
import com.example.docmind.viewmodel.StorageViewModel
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SettingsActivity : SettingsAwareActivity() {
    private val storageViewModel: StorageViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        findViewById<MaterialToolbar>(R.id.settingsToolbar).setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        listOf(R.id.settingsHeading, R.id.displayHeading, R.id.storageHeading, R.id.informationHeading).forEach {
            ViewCompat.setAccessibilityHeading(findViewById(it), true)
        }
        val settings = settingsRepository.load()
        val themeLabel = getString(themeLabel(settings.theme))
        val textSizeLabel = getString(textSizeLabel(settings.textSize))
        findViewById<TextView>(R.id.themeSummary).text = themeLabel
        findViewById<TextView>(R.id.textSizeSummary).text = textSizeLabel
        bindRow(R.id.themeRow, R.string.settings_theme_title, themeLabel, ::showThemeDialog)
        bindRow(R.id.textSizeRow, R.string.settings_text_size_title, textSizeLabel, ::showTextSizeDialog)
        bindLanguageRow()
        findViewById<View>(R.id.refreshStorage).setOnClickListener { storageViewModel.refresh() }
        findViewById<View>(R.id.clearCache).setOnClickListener { showClearCacheDialog() }
        storageViewModel.state.observe(this) { renderStorageState(it) }
        findViewById<View>(R.id.resetSettings).setOnClickListener { showResetDialog() }
        findViewById<TextView>(R.id.versionValue).text =
            getString(R.string.settings_version_value, installedVersion())

        savedInstanceState?.let { state ->
            val scroll = findViewById<NestedScrollView>(R.id.settingsScroll)
            scroll.post { scroll.scrollTo(0, state.getInt(SCROLL_POSITION)) }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(SCROLL_POSITION, findViewById<NestedScrollView>(R.id.settingsScroll).scrollY)
        super.onSaveInstanceState(outState)
    }

    private fun bindRow(id: Int, label: Int, value: String, action: () -> Unit) {
        val row = findViewById<View>(id)
        row.contentDescription = getString(R.string.settings_row_accessibility, getString(label), value)
        ViewCompat.setScreenReaderFocusable(row, true)
        ViewCompat.setAccessibilityDelegate(row, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = Button::class.java.name
            }
        })
        row.setOnClickListener { action() }
    }

    private fun showThemeDialog() {
        val current = settingsRepository.load()
        val choices = ThemeMode.entries
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_theme_title)
            .setSingleChoiceItems(
                choices.map { getString(themeLabel(it)) }.toTypedArray(),
                choices.indexOf(current.theme)
            ) { dialog, which ->
                dialog.dismiss()
                val latest = settingsRepository.load()
                val selected = choices[which]
                if (latest.theme != selected) {
                    settingsRepository.save(latest.copy(theme = selected))
                    recreate()
                }
            }
            .setNegativeButton(R.string.settings_cancel, null)
            .show()
    }

    private fun showTextSizeDialog() {
        val current = settingsRepository.load()
        val choices = TextSize.entries
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_text_size_title)
            .setSingleChoiceItems(
                choices.map { getString(textSizeLabel(it)) }.toTypedArray(),
                choices.indexOf(current.textSize)
            ) { dialog, which ->
                dialog.dismiss()
                val latest = settingsRepository.load()
                val selected = choices[which]
                if (latest.textSize != selected) {
                    settingsRepository.save(latest.copy(textSize = selected))
                    recreate()
                }
            }
            .setNegativeButton(R.string.settings_cancel, null)
            .show()
    }

    private fun showResetDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_reset_dialog_title)
            .setMessage(R.string.settings_reset_dialog_message)
            .setNegativeButton(R.string.settings_cancel, null)
            .setPositiveButton(R.string.settings_reset_confirm) { _, _ ->
                val previous = settingsRepository.load()
                val defaults = settingsRepository.reset()
                if (!AppCompatDelegate.getApplicationLocales().isEmpty) {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
                    bindLanguageRow()
                }
                if (previous != defaults) recreate()
            }
            .show()
    }

    private fun bindLanguageRow() {
        val language = AppLanguage.fromLanguageTags(
            AppCompatDelegate.getApplicationLocales().toLanguageTags()
        )
        val label = getString(languageLabel(language))
        findViewById<TextView>(R.id.languageSummary).text = label
        bindRow(R.id.languageRow, R.string.settings_language_title, label, ::showLanguageDialog)
    }

    private fun showLanguageDialog() {
        val current = AppLanguage.fromLanguageTags(
            AppCompatDelegate.getApplicationLocales().toLanguageTags()
        )
        val choices = AppLanguage.entries
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_language_title)
            .setSingleChoiceItems(
                choices.map { getString(languageLabel(it)) }.toTypedArray(),
                choices.indexOf(current)
            ) { dialog, which ->
                dialog.dismiss()
                val selected = choices[which]
                if (selected != current) {
                    AppCompatDelegate.setApplicationLocales(
                        LocaleListCompat.forLanguageTags(selected.languageTag)
                    )
                    bindLanguageRow()
                }
            }
            .setNegativeButton(R.string.settings_cancel, null)
            .show()
    }

    private fun languageLabel(language: AppLanguage): Int = when (language) {
        AppLanguage.SYSTEM -> R.string.settings_language_system
        AppLanguage.VIETNAMESE -> R.string.settings_language_vietnamese
        AppLanguage.ENGLISH -> R.string.settings_language_english
    }

    private fun showClearCacheDialog() {
        if (storageViewModel.state.value?.busy == true) return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_storage_clear_title)
            .setMessage(R.string.settings_storage_clear_message)
            .setNegativeButton(R.string.settings_cancel, null)
            .setPositiveButton(R.string.settings_storage_clear_confirm) { _, _ ->
                storageViewModel.clearCache()
            }
            .show()
    }

    private fun renderStorageState(state: StorageUiState) {
        val usage = state.usage
        val pending = getString(R.string.settings_storage_pending)
        findViewById<TextView>(R.id.dataBytesValue).text =
            usage?.let { Formatter.formatFileSize(this, it.dataBytes) } ?: pending
        findViewById<TextView>(R.id.cacheBytesValue).text =
            usage?.let { Formatter.formatFileSize(this, it.cacheBytes) } ?: pending
        findViewById<TextView>(R.id.totalBytesValue).text =
            usage?.let { Formatter.formatFileSize(this, it.totalBytes) } ?: pending
        findViewById<View>(R.id.storageProgress).visibility =
            if (state.busy) View.VISIBLE else View.GONE
        findViewById<View>(R.id.refreshStorage).isEnabled = !state.busy
        findViewById<View>(R.id.clearCache).isEnabled =
            !state.busy && usage != null && (usage.cacheBytes > 0 || usage.unreadableEntries > 0)

        val messages = mutableListOf<String>()
        when {
            state.busy -> messages += getString(
                if (state.clearing) R.string.settings_storage_clearing
                else R.string.settings_storage_loading
            )
            state.failed -> messages += getString(R.string.settings_storage_error)
            state.clearResult != null -> {
                val result = state.clearResult
                val bytes = Formatter.formatFileSize(this, result.deletedBytes)
                messages += if (result.failedEntries == 0) {
                    getString(R.string.settings_storage_clear_done, bytes)
                } else {
                    getString(R.string.settings_storage_clear_partial, bytes, result.failedEntries)
                }
            }
            usage?.cacheBytes == 0L -> messages += getString(R.string.settings_storage_no_cache)
        }
        if (!state.busy && usage != null && usage.unreadableEntries > 0) {
            messages += getString(R.string.settings_storage_scan_partial, usage.unreadableEntries)
        }
        findViewById<TextView>(R.id.storageStatus).apply {
            text = messages.joinToString("\n")
            visibility = if (messages.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun themeLabel(theme: ThemeMode): Int = when (theme) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
    }

    private fun textSizeLabel(size: TextSize): Int = when (size) {
        TextSize.DEFAULT -> R.string.settings_text_size_default
        TextSize.LARGE -> R.string.settings_text_size_large
        TextSize.EXTRA_LARGE -> R.string.settings_text_size_extra_large
    }

    @Suppress("DEPRECATION")
    private fun installedVersion(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName
            ?.takeIf { it.isNotBlank() }
            ?: getString(R.string.settings_version_unknown)
    } catch (_: PackageManager.NameNotFoundException) {
        getString(R.string.settings_version_unknown)
    }

    private companion object {
        const val SCROLL_POSITION = "settings_scroll_position"
    }
}
