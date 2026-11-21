package com.example.docmind.data.local

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("docmind_session_pref", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_LOGGED_IN_USER_ID = "logged_in_user_id"
    }

    fun saveLoggedInUserId(userId: Long) {
        prefs.edit().putLong(KEY_LOGGED_IN_USER_ID, userId).apply()
    }

    fun getLoggedInUserId(): Long? {
        val id = prefs.getLong(KEY_LOGGED_IN_USER_ID, -1L)
        return if (id != -1L) id else null
    }

    fun clearSession() {
        prefs.edit().remove(KEY_LOGGED_IN_USER_ID).apply()
    }
}
