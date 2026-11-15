package com.example.service

import android.content.Context
import android.os.Build
import java.util.UUID

class DeviceIdentityStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getOrCreateDeviceId(): String {
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val generated = "dev_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        prefs.edit().putString(KEY_DEVICE_ID, generated).apply()
        return generated
    }

    fun getDeviceName(): String {
        val saved = prefs.getString(KEY_DEVICE_NAME, null)
        if (!saved.isNullOrBlank()) return saved
        val model = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}".trim()
        return model.take(60).ifBlank { "Android Phone" }
    }

    fun setDeviceName(name: String) {
        val clean = name.trim().take(60)
        if (clean.isNotBlank()) {
            prefs.edit().putString(KEY_DEVICE_NAME, clean).apply()
        }
    }

    fun getUpdateIntervalSec(): Long {
        return prefs.getLong(KEY_INTERVAL_SEC, 10L).coerceIn(5L, 300L)
    }

    fun setUpdateIntervalSec(seconds: Long) {
        prefs.edit().putLong(KEY_INTERVAL_SEC, seconds.coerceIn(5L, 300L)).apply()
    }

    fun isTheftAlertEnabled(): Boolean {
        return prefs.getBoolean(KEY_THEFT_ALERT, false)
    }

    fun setTheftAlertEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_THEFT_ALERT, enabled).apply()
    }

    fun isTrackingArmed(): Boolean {
        return prefs.getBoolean(KEY_TRACKING_ARMED, false)
    }

    fun setTrackingArmed(armed: Boolean) {
        prefs.edit().putBoolean(KEY_TRACKING_ARMED, armed).apply()
    }

    private companion object {
        const val PREFS_NAME = "sentinel_device_identity"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_DEVICE_NAME = "device_name"
        const val KEY_INTERVAL_SEC = "interval_sec"
        const val KEY_THEFT_ALERT = "theft_alert"
        const val KEY_TRACKING_ARMED = "tracking_armed"
    }
}
