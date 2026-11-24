package com.example.docmind.viewmodel

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class UiMessages(context: Context) {
    private val appContext = context.applicationContext
    private val message = MutableStateFlow<String?>(null)
    val status = message.asStateFlow()

    fun show(value: String) { message.value = value }
    fun clear() { message.value = null }

    fun text(@StringRes id: Int, vararg args: Any): String {
        val configuration = Configuration(appContext.resources.configuration)
        val locales = AppCompatDelegate.getApplicationLocales()
        if (!locales.isEmpty) configuration.setLocales(LocaleList.forLanguageTags(locales.toLanguageTags()))
        return appContext.createConfigurationContext(configuration).getString(id, *args)
    }
}
