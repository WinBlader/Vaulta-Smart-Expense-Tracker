package com.waleed.vaulta.android.data.budget

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stateful notification throttle backed by SharedPreferences.
 * Implements a strict rate limiter for both Negative Balance and Monthly Budget Exceeded:
 * - Notification #1 sent immediately upon threshold breach (sentCount = 1, lastSentAt = currentTime).
 * - Notification #2 permitted ONLY once after at least a 30-second gap (sentCount == 1 and elapsed >= 30,000ms),
 *   advancing sentCount to 2 and completely suppressing further notifications.
 * - Reset back to 0 ONLY when the breach condition recovers.
 */
@Singleton
class NotificationAlertManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "vaulta_alert_throttle_prefs"
        private const val THROTTLE_GAP_MILLIS = 30_000L // 30 seconds
        private const val MAX_ALERTS = 2

        private const val KEY_PREFIX_NEG_COUNT = "neg_balance_sent_count_"
        private const val KEY_PREFIX_NEG_TIME = "neg_balance_last_sent_at_"

        private const val KEY_PREFIX_BUDGET_COUNT = "budget_exceeded_sent_count_"
        private const val KEY_PREFIX_BUDGET_TIME = "budget_exceeded_last_sent_at_"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun shouldSendNegativeBalanceAlert(userKey: String): Boolean {
        val countKey = "$KEY_PREFIX_NEG_COUNT$userKey"
        val timeKey = "$KEY_PREFIX_NEG_TIME$userKey"

        val sentCount = prefs.getInt(countKey, 0)
        val lastSentAt = prefs.getLong(timeKey, 0L)
        val now = System.currentTimeMillis()

        return when {
            sentCount == 0 -> {
                // Send Notification #1
                prefs.edit()
                    .putInt(countKey, 1)
                    .putLong(timeKey, now)
                    .apply()
                true
            }
            sentCount == 1 -> {
                if (now - lastSentAt >= THROTTLE_GAP_MILLIS) {
                    // Send Notification #2 (final)
                    prefs.edit()
                        .putInt(countKey, 2)
                        .putLong(timeKey, now)
                        .apply()
                    true
                } else {
                    false
                }
            }
            else -> false // sentCount >= 2: completely suppressed
        }
    }

    @Synchronized
    fun resetNegativeBalanceAlert(userKey: String) {
        val countKey = "$KEY_PREFIX_NEG_COUNT$userKey"
        val timeKey = "$KEY_PREFIX_NEG_TIME$userKey"
        if (prefs.getInt(countKey, 0) != 0) {
            prefs.edit()
                .putInt(countKey, 0)
                .putLong(timeKey, 0L)
                .apply()
        }
    }

    @Synchronized
    fun shouldSendBudgetExceededAlert(userKey: String): Boolean {
        val countKey = "$KEY_PREFIX_BUDGET_COUNT$userKey"
        val timeKey = "$KEY_PREFIX_BUDGET_TIME$userKey"

        val sentCount = prefs.getInt(countKey, 0)
        val lastSentAt = prefs.getLong(timeKey, 0L)
        val now = System.currentTimeMillis()

        return when {
            sentCount == 0 -> {
                // Send Notification #1
                prefs.edit()
                    .putInt(countKey, 1)
                    .putLong(timeKey, now)
                    .apply()
                true
            }
            sentCount == 1 -> {
                if (now - lastSentAt >= THROTTLE_GAP_MILLIS) {
                    // Send Notification #2 (final)
                    prefs.edit()
                        .putInt(countKey, 2)
                        .putLong(timeKey, now)
                        .apply()
                    true
                } else {
                    false
                }
            }
            else -> false // sentCount >= 2: completely suppressed
        }
    }

    @Synchronized
    fun resetBudgetExceededAlert(userKey: String) {
        val countKey = "$KEY_PREFIX_BUDGET_COUNT$userKey"
        val timeKey = "$KEY_PREFIX_BUDGET_TIME$userKey"
        if (prefs.getInt(countKey, 0) != 0) {
            prefs.edit()
                .putInt(countKey, 0)
                .putLong(timeKey, 0L)
                .apply()
        }
    }

    @Synchronized
    fun clearForUser(userKey: String) {
        prefs.edit()
            .remove("$KEY_PREFIX_NEG_COUNT$userKey")
            .remove("$KEY_PREFIX_NEG_TIME$userKey")
            .remove("$KEY_PREFIX_BUDGET_COUNT$userKey")
            .remove("$KEY_PREFIX_BUDGET_TIME$userKey")
            .apply()
    }
}
