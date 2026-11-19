package com.waleed.vaulta.android.data.budget

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.waleed.vaulta.android.MainActivity
import com.waleed.vaulta.android.R
import com.waleed.vaulta.android.utils.Utils
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BudgetNotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_ID = "vaulta_budget_channel"
        const val CHANNEL_NAME = "Vaulta Budget Alerts"
        const val CHANNEL_DESC = "Notifications for spending budget warnings and limits"
        const val NOTIFICATION_ID_WARNING = 1001
        const val NOTIFICATION_ID_EXCEEDED = 1002
        const val NOTIFICATION_ID_NEGATIVE_BALANCE = 1003
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 150, 300)
            }
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun createContentIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getActivity(context, 0, intent, flags)
    }

    /**
     * Renders the custom Vaulta app icon into a Bitmap for notification display.
     */
    private fun getVaultaAppIconBitmap(): Bitmap? {
        return try {
            val size = 192
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val bg = ContextCompat.getDrawable(context, R.drawable.ic_launcher_background)
            bg?.setBounds(0, 0, size, size)
            bg?.draw(canvas)

            val fg = ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)
            fg?.setBounds(0, 0, size, size)
            fg?.draw(canvas)

            bitmap
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Warns the user when their spending is approaching the limit (>= 80% and < 100%).
     */
    fun sendBudgetWarningNotification(currentSpend: Double, budgetLimit: Double) {
        if (!hasNotificationPermission()) return

        val percent = if (budgetLimit > 0) ((currentSpend / budgetLimit) * 100).toInt() else 0
        val remaining = (budgetLimit - currentSpend).coerceAtLeast(0.0)

        val title = "⚠️ Budget Alert: Approaching Limit ($percent%)"
        val message = "You've spent ${Utils.formatCurrency(currentSpend)} of your ${Utils.formatCurrency(budgetLimit)} budget. Only ${Utils.formatCurrency(remaining)} remaining!"

        val appIconBitmap = getVaultaAppIconBitmap()
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_vaulta_notification)
            .setColor(Color.parseColor("#2F7E79"))
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(createContentIntent())

        if (appIconBitmap != null) {
            builder.setLargeIcon(appIconBitmap)
        }

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_WARNING, builder.build())
        } catch (_: SecurityException) {
            // Handled when permission is not granted
        }
    }

    /**
     * Alerts the user when their spending has reached or exceeded their budget limit (>= 100%).
     */
    fun sendBudgetExceededNotification(currentSpend: Double, budgetLimit: Double) {
        if (!hasNotificationPermission()) return

        val exceededAmount = (currentSpend - budgetLimit).coerceAtLeast(0.0)
        val title = "🚨 Monthly Spending Budget Reached!"
        val message = if (exceededAmount > 0) {
            "You have exceeded your monthly budget of ${Utils.formatCurrency(budgetLimit)} by ${Utils.formatCurrency(exceededAmount)}! (Total: ${Utils.formatCurrency(currentSpend)})"
        } else {
            "You have reached 100% of your monthly budget limit (${Utils.formatCurrency(budgetLimit)})!"
        }

        val appIconBitmap = getVaultaAppIconBitmap()
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_vaulta_notification)
            .setColor(Color.parseColor("#2F7E79"))
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(createContentIntent())

        if (appIconBitmap != null) {
            builder.setLargeIcon(appIconBitmap)
        }

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_EXCEEDED, builder.build())
        } catch (_: SecurityException) {
            // Handled when permission is not granted
        }
    }

    /**
     * Alerts the user immediately when their total balance goes negative (expenses exceed total income).
     */
    fun sendNegativeBalanceNotification(currentBalance: Double, totalIncome: Double, totalExpense: Double) {
        if (!hasNotificationPermission()) return

        val title = "🚨 Negative Balance Warning!"
        val message = "Your total balance has dropped below zero to ${Utils.formatCurrency(currentBalance)}. Your expenses (${Utils.formatCurrency(totalExpense)}) exceed your income (${Utils.formatCurrency(totalIncome)})."

        val appIconBitmap = getVaultaAppIconBitmap()
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_vaulta_notification)
            .setColor(Color.parseColor("#D32F2F"))
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(createContentIntent())

        if (appIconBitmap != null) {
            builder.setLargeIcon(appIconBitmap)
        }

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_NEGATIVE_BALANCE, builder.build())
        } catch (_: SecurityException) {
            // Handled when permission is not granted
        }
    }
}
