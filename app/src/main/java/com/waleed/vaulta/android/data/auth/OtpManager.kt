package com.waleed.vaulta.android.data.auth

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.waleed.vaulta.android.MainActivity
import com.waleed.vaulta.android.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

data class ActiveOtp(
    val phoneNumber: String,
    val code: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    val isExpired: Boolean
        get() = (System.currentTimeMillis() - createdAt) > (5 * 60 * 1000) // 5 minutes
}

@Singleton
class OtpManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_ID = "vaulta_auth_otp_channel"
        const val NOTIFICATION_ID = 1001
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val _activeOtp = MutableStateFlow<ActiveOtp?>(null)
    val activeOtp: StateFlow<ActiveOtp?> = _activeOtp.asStateFlow()

    private val _incomingOtpAlert = MutableStateFlow<String?>(null)
    val incomingOtpAlert: StateFlow<String?> = _incomingOtpAlert.asStateFlow()

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Vaulta Security Codes",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Verification OTP notifications for Vaulta authentication"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun normalizePhone(rawPhone: String): String {
        val digits = rawPhone.filter { it.isDigit() }
        return if (digits.length >= 10) digits.takeLast(10) else digits
    }

    fun sendOtp(phoneNumber: String): String {
        val normalized = normalizePhone(phoneNumber)
        val code = String.format("%06d", Random.nextInt(100000, 1000000))
        val active = ActiveOtp(phoneNumber = normalized, code = code)
        _activeOtp.value = active

        // Send real Android notification
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_lock)
                .setContentTitle("Vaulta Verification Code")
                .setContentText("Your OTP is $code. Valid for 5 minutes.")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("Your Vaulta verification code is: $code\nUse this code to verify your mobile number. Do not share this OTP with anyone.")
                )
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Post in-app alert for immediate visibility and auto-fill
        _incomingOtpAlert.value = code

        return code
    }

    fun verifyOtp(phoneNumber: String, enteredCode: String): Boolean {
        val current = _activeOtp.value ?: return false
        val normalized = normalizePhone(phoneNumber)
        if (current.phoneNumber != normalized) return false
        if (current.isExpired) return false
        if (current.code == enteredCode.trim()) {
            _activeOtp.value = null
            _incomingOtpAlert.value = null
            return true
        }
        return false
    }

    fun dismissAlert() {
        _incomingOtpAlert.value = null
    }
}
