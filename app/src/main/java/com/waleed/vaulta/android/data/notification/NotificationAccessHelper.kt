package com.waleed.vaulta.android.data.notification

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat

object NotificationAccessHelper {

    private const val PREFS_NAME = "vaulta_notification_prefs"
    private const val KEY_IS_AUTO_DETECT_PREFIX = "is_auto_detect_enabled_"
    private const val KEY_IS_AUTO_DETECT_GLOBAL = "is_auto_detect_enabled_global"

    /**
     * Checks whether Vaulta's NotificationListenerService is enabled in system settings.
     */
    fun isNotificationListenerEnabled(context: Context): Boolean {
        val packageName = context.packageName
        val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
        if (enabledPackages.contains(packageName)) return true

        // Fallback check against Settings.Secure
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(packageName)
    }

    /**
     * Checks whether the NotificationListenerService is currently connected and active.
     */
    fun isServiceConnected(): Boolean {
        return VaultaNotificationListenerService.isServiceConnected
    }

    /**
     * Requests rebind of the NotificationListenerService if permission is granted.
     * Essential for MIUI / HyperOS (Xiaomi / Redmi) after app boot or memory pressure.
     */
    fun checkAndRequestRebind(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isNotificationListenerEnabled(context)) {
            try {
                NotificationListenerService.requestRebind(
                    ComponentName(context, VaultaNotificationListenerService::class.java)
                )
            } catch (_: Exception) {}
        }
    }

    /**
     * Builds an Intent to open the Android Notification Access system settings.
     */
    fun getNotificationListenerSettingsIntent(): Intent {
        return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * Builds an Intent pointing to Vaulta's App Details Settings (App Info).
     * Useful for:
     * 1. Android 13+ sideloaded APKs where the user must tap the top-right menu and choose "Allow restricted settings".
     * 2. OEM devices (Xiaomi/Redmi/Oppo/Vivo/Samsung) where the user sets Battery Saver to "No restrictions".
     */
    fun getAppDetailsSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * Checks whether Vaulta is exempt from aggressive OEM background battery optimizations.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
        } else {
            true
        }
    }

    /**
     * Returns whether the user has toggled ON the Auto-Detect feature.
     * Supports both userKey-specific and global fallback flags to prevent state mismatch.
     */
    fun isAutoDetectEnabled(context: Context, userKey: String = "guest"): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_IS_AUTO_DETECT_PREFIX + userKey, false) ||
                prefs.getBoolean(KEY_IS_AUTO_DETECT_GLOBAL, false)
    }

    /**
     * Persists the Auto-Detect enabled toggle for the specified user account and global fallback.
     */
    fun setAutoDetectEnabled(context: Context, enabled: Boolean, userKey: String = "guest") {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_IS_AUTO_DETECT_PREFIX + userKey, enabled)
            .putBoolean(KEY_IS_AUTO_DETECT_GLOBAL, enabled)
            .apply()
    }

    /**
     * Resets/removes the Auto-Detect toggle for the specified user account (e.g. on logout).
     */
    fun resetAutoDetect(context: Context, userKey: String = "guest") {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_IS_AUTO_DETECT_PREFIX + userKey)
            .remove(KEY_IS_AUTO_DETECT_GLOBAL)
            .apply()
    }
}
