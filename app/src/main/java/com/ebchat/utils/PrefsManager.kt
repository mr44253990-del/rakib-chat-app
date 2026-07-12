package com.ebchat.utils

import android.content.Context
import android.content.SharedPreferences
import com.ebchat.EBChatApp

class PrefsManager private constructor() {

    private val prefs: SharedPreferences by lazy {
        EBChatApp.instance.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    }

    companion object {
        @Volatile
        private var instance: PrefsManager? = null

        fun getInstance(): PrefsManager {
            return instance ?: synchronized(this) {
                instance ?: PrefsManager().also { instance = it }
            }
        }
    }

    // First launch
    var isFirstLaunch: Boolean
        get() = prefs.getBoolean(Constants.PREF_FIRST_LAUNCH, true)
        set(value) = prefs.edit().putBoolean(Constants.PREF_FIRST_LAUNCH, value).apply()

    // User ID
    var userId: String?
        get() = prefs.getString(Constants.PREF_USER_ID, null)
        set(value) = prefs.edit().putString(Constants.PREF_USER_ID, value).apply()

    // Dark mode
    var isDarkMode: Boolean
        get() = prefs.getBoolean(Constants.PREF_DARK_MODE, true)
        set(value) = prefs.edit().putBoolean(Constants.PREF_DARK_MODE, value).apply()

    // Notifications
    var isNotificationsEnabled: Boolean
        get() = prefs.getBoolean(Constants.PREF_NOTIFICATIONS, true)
        set(value) = prefs.edit().putBoolean(Constants.PREF_NOTIFICATIONS, value).apply()

    // Sound
    var isSoundEnabled: Boolean
        get() = prefs.getBoolean(Constants.PREF_SOUND, true)
        set(value) = prefs.edit().putBoolean(Constants.PREF_SOUND, value).apply()

    // Vibration
    var isVibrationEnabled: Boolean
        get() = prefs.getBoolean(Constants.PREF_VIBRATION, true)
        set(value) = prefs.edit().putBoolean(Constants.PREF_VIBRATION, value).apply()

    // Show online status
    var showOnlineStatus: Boolean
        get() = prefs.getBoolean(Constants.PREF_SHOW_ONLINE, true)
        set(value) = prefs.edit().putBoolean(Constants.PREF_SHOW_ONLINE, value).apply()

    // Read receipts
    var readReceipts: Boolean
        get() = prefs.getBoolean(Constants.PREF_READ_RECEIPTS, true)
        set(value) = prefs.edit().putBoolean(Constants.PREF_READ_RECEIPTS, value).apply()

    // Clear all prefs on logout
    fun clear() {
        prefs.edit().clear().apply()
    }

    // Generic helpers
    fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
    fun getString(key: String, default: String = ""): String = prefs.getString(key, default) ?: default
    fun putBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    fun getBoolean(key: String, default: Boolean = false): Boolean = prefs.getBoolean(key, default)
    fun putLong(key: String, value: Long) = prefs.edit().putLong(key, value).apply()
    fun getLong(key: String, default: Long = 0): Long = prefs.getLong(key, default)
}
