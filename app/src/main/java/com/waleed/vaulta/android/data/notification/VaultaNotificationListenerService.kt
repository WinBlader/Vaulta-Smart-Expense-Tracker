package com.waleed.vaulta.android.data.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.waleed.vaulta.android.MainActivity
import com.waleed.vaulta.android.R
import com.waleed.vaulta.android.data.CurrencyManager
import com.waleed.vaulta.android.data.model.ExpenseEntity
import com.waleed.vaulta.android.data.repository.ExpenseRepository
import com.waleed.vaulta.android.data.session.UserSessionManager
import com.waleed.vaulta.android.utils.Utils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class VaultaNotificationListenerService : NotificationListenerService() {

    @Inject
    lateinit var expenseRepository: ExpenseRepository

    @Inject
    lateinit var userSessionManager: UserSessionManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val CHANNEL_ID = "vaulta_autodetect_channel"
        const val CHANNEL_NAME = "Vaulta Auto-Detect"
        const val CHANNEL_DESC = "Confirms transactions automatically logged from bank and payment alerts"
        private var notificationCounter = 2000
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        // Automatically request rebind when memory pressure releases
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                requestRebind(ComponentName(this, VaultaNotificationListenerService::class.java))
            } catch (_: Exception) {}
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)

        if (sbn == null || sbn.isOngoing) return
        if (sbn.packageName == packageName) return

        val userKey = userSessionManager.getCurrentUserKey()

        // 1. Check if user has enabled Auto-Detect
        if (!NotificationAccessHelper.isAutoDetectEnabled(this, userKey)) {
            return
        }

        // 2. Ensure an active session exists (either logged in or active guest mode)
        if (!userSessionManager.isLoggedIn() && userKey != "guest") {
            return
        }

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()

        serviceScope.launch {
            try {
                val parsed = TransactionNotificationParser.parse(
                    packageName = sbn.packageName,
                    title = title,
                    text = text,
                    bigText = bigText,
                    postTime = sbn.postTime
                ) ?: return@launch

                // Deduplicate simultaneous SMS + Push alerts for the same transaction
                if (TransactionDeduplicator.isDuplicate(parsed.amount, parsed.type, parsed.timestamp)) {
                    return@launch
                }

                // If detected currency differs from user's configured currency, note it in title
                val selectedCurrency = CurrencyManager.getSelectedCurrency()
                val displayTitle = if (parsed.currencyCode != null &&
                    !parsed.currencyCode.equals(selectedCurrency.code, ignoreCase = true)
                ) {
                    "${parsed.title} [${parsed.currencyCode}]"
                } else {
                    parsed.title
                }

                val expense = ExpenseEntity(
                    id = null,
                    title = displayTitle,
                    amount = parsed.amount,
                    date = Utils.formatDateToHumanReadableForm(parsed.timestamp),
                    type = parsed.type,
                    userId = userKey
                )

                val inserted = expenseRepository.insertExpense(expense)
                if (inserted) {
                    postConfirmationNotification(parsed)
                }
            } catch (_: Exception) {
                // Fail gracefully without crashing the system listener service
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = CHANNEL_DESC
                enableVibration(true)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    private fun postConfirmationNotification(transaction: ParsedTransaction) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) return
        }

        val symbol = transaction.originalCurrencySymbol ?: CurrencyManager.getSelectedCurrencySymbol()
        val formattedAmount = Utils.formatCurrency(transaction.amount)
        val merchantDisplay = transaction.rawMerchant ?: transaction.category
        val contentText = "Vaulta logged $formattedAmount ${transaction.type} • $merchantDisplay"

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, openIntent, flags)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_vaulta_notification)
            .setColor(Color.parseColor("#2F7E79"))
            .setContentTitle("Transaction Auto-Detected")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            val notificationId = synchronized(this) { notificationCounter++ }
            NotificationManagerCompat.from(this).notify(notificationId, builder.build())
        } catch (_: SecurityException) {
            // Handled when POST_NOTIFICATIONS is disabled
        }
    }
}
