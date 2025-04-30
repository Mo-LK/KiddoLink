package com.childapp.storage

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("childapp_prefs", Context.MODE_PRIVATE)

    fun saveId(id: String) {
        prefs.edit().putString("device_id", id).apply()
    }

    fun getId(): String? {
        return prefs.getString("device_id", null)
    }

    fun hasId(): Boolean {
        return prefs.contains("device_id")
    }
}
