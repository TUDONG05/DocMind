package com.example.docmind.settings

import android.content.Context

class SharedPreferencesSettingsStorage(context: Context) : SettingsStorage {
    private val preferences = context.applicationContext.getSharedPreferences(
        "docmind_settings", Context.MODE_PRIVATE
    )

    // Older app versions may have stored another value type for a setting.
    override fun read(key: String): String? = preferences.all[key] as? String

    override fun write(values: Map<String, String>) {
        val editor = preferences.edit()
        values.forEach { (key, value) -> editor.putString(key, value) }
        editor.apply()
    }
}
