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
import android.util.Log
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
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
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

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ListenerEntryPoint {
        fun expenseRepository(): ExpenseRepository
        fun userSessionManager(): UserSessionManager
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "VaultaAutoDetect"
        const val CHANNEL_ID = "vaulta_autodetect_channel"
        const val CHANNEL_NAME = "Vaulta Auto-Detect"
        const val CHANNEL_DESC = "Confirms transactions automatically logged from bank and payment alerts"
        private var notificationCounter = 2000

        @Volatile
        var isServiceConnected: Boolean = false
    }

    override fun onCreate() {
        super.onCreate()
        ensureDependencies()
        createNotificationChannel()
        Log.d(TAG, "VaultaNotificationListenerService created")
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceConnected = false
        serviceScope.cancel()
        Log.d(TAG, "VaultaNotificationListenerService destroyed")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isServiceConnected = true
        Log.d(TAG, "Notification listener connected successfully")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isServiceConnected = false
        Log.d(TAG, "Notification listener disconnected, requesting rebind")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                requestRebind(ComponentName(this, VaultaNotificationListenerService::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to request rebind", e)
            }
        }
    }

    private fun ensureDependencies() {
        if (!::expenseRepository.isInitialized || !::userSessionManager.isInitialized) {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    applicationContext,
                    ListenerEntryPoint::class.java
                )
                if (!::expenseRepository.isInitialized) {
                    expenseRepository = entryPoint.expenseRepository()
                }
                if (!::userSessionManager.isInitialized) {
                    userSessionManager = entryPoint.userSessionManager()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resolve Hilt dependencies via EntryPoint", e)
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)

        if (sbn == null) return
        if (sbn.packageName == packageName) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        ensureDependencies()

        val userKey = if (::userSessionManager.isInitialized) {
            userSessionManager.getCurrentUserKey()
        } else {
            "guest"
        }

        // 1. Check if user has enabled Auto-Detect
        if (!NotificationAccessHelper.isAutoDetectEnabled(this, userKey)) {
            return
        }

        // 2. Safe text extraction supporting CharSequence / SpannableString across all Android notification styles
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim()
        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.filterNotNull()
            ?.map { it.toString().trim() }
            ?.filter { it.isNotBlank() }
            ?.joinToString(" ")

        // Skip empty group summary notifications
        val isGroupSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        if (isGroupSummary && text.isBlank() && bigText.isNullOrBlank() && textLines.isNullOrBlank()) {
            return
        }

        if (title.isBlank() && text.isBlank() && bigText.isNullOrBlank() && textLines.isNullOrBlank()) {
            return
        }

        Log.d(TAG, "Notification received from pkg=${sbn.packageName}, titleLen=${title.length}, textLen=${text.length}, hasBigText=${bigText != null}, hasLines=${textLines != null}")

        serviceScope.launch {
            try {
                val parsed = TransactionNotificationParser.parse(
                    packageName = sbn.packageName,
                    title = title,
                    text = text,
                    bigText = bigText,
                    subText = subText,
                    textLines = textLines,
                    postTime = sbn.postTime
                )

                if (parsed == null) {
                    Log.d(TAG, "Notification skipped: not recognized as completed financial transaction from pkg=${sbn.packageName}")
                    return@launch
                }

                // Deduplicate simultaneous SMS + Push alerts for the same transaction
                val merchantKey = parsed.rawMerchant ?: parsed.title
                if (TransactionDeduplicator.isDuplicate(parsed.amount, parsed.type, merchantKey, parsed.timestamp)) {
                    Log.d(TAG, "Duplicate transaction suppressed: ${parsed.amount} ${parsed.type} ($merchantKey)")
                    return@launch
                }

                // If user hasn't explicitly customized their currency yet, adopt detected currency
                if (!CurrencyManager.hasUserSetCurrency() && parsed.currencyCode != null) {
                    CurrencyManager.setCurrencyByCode(parsed.currencyCode)
                }

                // Clean title without raw currency code brackets
                val displayTitle = parsed.title.replace(Regex("""(?i)\s*\[[A-Z]{3}\]"""), "").trim()

                val expense = ExpenseEntity(
                    id = null,
                    title = displayTitle,
                    amount = parsed.amount,
                    date = Utils.formatDateToHumanReadableForm(parsed.timestamp),
                    type = parsed.type,
                    userId = userKey
                )

                val inserted = expenseRepository.insertExpense(expense)
                Log.d(TAG, "Transaction auto-logged to DB: success=$inserted, amount=${parsed.amount}, type=${parsed.type}, userKey=$userKey, title=$displayTitle")

                if (inserted) {
                    postConfirmationNotification(parsed)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing notification in listener service", e)
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
