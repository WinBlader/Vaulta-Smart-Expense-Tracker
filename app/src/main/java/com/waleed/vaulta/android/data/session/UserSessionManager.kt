package com.waleed.vaulta.android.data.session

import android.content.Context
import android.content.SharedPreferences
import com.waleed.vaulta.android.data.model.UserProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserSessionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("vaulta_user_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_PHONE = "user_phone"
        private const val KEY_AUTH_PROVIDER = "auth_provider"
        private const val KEY_IS_VERIFIED = "is_verified"
        private const val KEY_REGISTERED_AT = "registered_at"
        private const val KEY_REGISTERED_ACCOUNTS = "registered_accounts_json"
        private const val KEY_THEME_MODE = "app_theme_mode"
    }

    private val _themeMode = MutableStateFlow(prefs.getString(KEY_THEME_MODE, "SYSTEM") ?: "SYSTEM")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    fun setThemeMode(mode: String) {
        prefs.edit().putString(KEY_THEME_MODE, mode).apply()
        _themeMode.value = mode
    }

    private val _currentUser = MutableStateFlow<UserProfile?>(loadUserFromPrefs())
    val currentUser: StateFlow<UserProfile?> = _currentUser.asStateFlow()

    fun isLoggedIn(): Boolean {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    private fun loadUserFromPrefs(): UserProfile? {
        if (!isLoggedIn()) return null
        val name = prefs.getString(KEY_USER_NAME, "") ?: ""
        val email = prefs.getString(KEY_USER_EMAIL, "") ?: ""
        val phone = prefs.getString(KEY_USER_PHONE, "") ?: ""
        val provider = prefs.getString(KEY_AUTH_PROVIDER, "Supabase Auth (Verified)") ?: "Supabase Auth (Verified)"
        val isVerified = prefs.getBoolean(KEY_IS_VERIFIED, true)
        val registeredAt = prefs.getLong(KEY_REGISTERED_AT, System.currentTimeMillis())
        return UserProfile(
            name = name,
            email = email,
            phoneNumber = phone,
            authProvider = provider,
            isVerified = isVerified,
            registeredAt = registeredAt
        )
    }

    fun saveUser(user: UserProfile) {
        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putString(KEY_USER_NAME, user.name)
            .putString(KEY_USER_EMAIL, user.email)
            .putString(KEY_USER_PHONE, user.phoneNumber)
            .putString(KEY_AUTH_PROVIDER, user.authProvider)
            .putBoolean(KEY_IS_VERIFIED, user.isVerified)
            .putLong(KEY_REGISTERED_AT, user.registeredAt)
            .apply()
        _currentUser.value = user

        // Persist to registered accounts store
        saveRegisteredAccount(user)
    }

    fun updateUser(name: String, email: String, phoneNumber: String) {
        val currentProvider = _currentUser.value?.authProvider ?: "Supabase Auth (Verified)"
        val isVerified = _currentUser.value?.isVerified ?: true
        val registeredAt = _currentUser.value?.registeredAt ?: System.currentTimeMillis()

        val updatedUser = UserProfile(
            name = name,
            email = email,
            phoneNumber = phoneNumber,
            authProvider = currentProvider,
            isVerified = isVerified,
            registeredAt = registeredAt
        )
        saveUser(updatedUser)
    }

    fun loginAsGuest() {
        val guestUser = UserProfile(
            name = "Guest User",
            email = "",
            phoneNumber = "",
            authProvider = "Guest Mode",
            isVerified = false,
            registeredAt = System.currentTimeMillis()
        )
        saveUser(guestUser)
    }

    fun logout() {
        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, false)
            .remove(KEY_USER_NAME)
            .remove(KEY_USER_EMAIL)
            .remove(KEY_USER_PHONE)
            .remove(KEY_AUTH_PROVIDER)
            .remove(KEY_IS_VERIFIED)
            .remove(KEY_REGISTERED_AT)
            .apply()
        _currentUser.value = null
    }

    // ==========================================
    // Persistent Account Registry Management
    // ==========================================

    private fun normalizePhone(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        return if (digits.length >= 10) digits.takeLast(10) else digits
    }

    fun getAllRegisteredAccounts(): List<UserProfile> {
        val jsonString = prefs.getString(KEY_REGISTERED_ACCOUNTS, null) ?: return emptyList()
        val list = mutableListOf<UserProfile>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    UserProfile(
                        name = obj.optString("name", ""),
                        email = obj.optString("email", ""),
                        phoneNumber = obj.optString("phoneNumber", ""),
                        authProvider = obj.optString("authProvider", "Supabase Auth (Verified)"),
                        isVerified = obj.optBoolean("isVerified", true),
                        registeredAt = obj.optLong("registeredAt", System.currentTimeMillis())
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun saveRegisteredAccount(user: UserProfile) {
        val currentAccounts = getAllRegisteredAccounts().toMutableList()
        val normalizedPhone = normalizePhone(user.phoneNumber)

        // Find existing index by normalized phone or email
        val existingIndex = currentAccounts.indexOfFirst {
            (normalizedPhone.isNotEmpty() && normalizePhone(it.phoneNumber) == normalizedPhone) ||
            (user.email.isNotEmpty() && it.email.equals(user.email, ignoreCase = true))
        }

        if (existingIndex >= 0) {
            currentAccounts[existingIndex] = user
        } else {
            currentAccounts.add(user)
        }

        try {
            val jsonArray = JSONArray()
            for (acc in currentAccounts) {
                val obj = JSONObject().apply {
                    put("name", acc.name)
                    put("email", acc.email)
                    put("phoneNumber", acc.phoneNumber)
                    put("authProvider", acc.authProvider)
                    put("isVerified", acc.isVerified)
                    put("registeredAt", acc.registeredAt)
                }
                jsonArray.put(obj)
            }
            prefs.edit().putString(KEY_REGISTERED_ACCOUNTS, jsonArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun findAccountByPhone(phone: String): UserProfile? {
        val normalized = normalizePhone(phone)
        if (normalized.isEmpty()) return null
        return getAllRegisteredAccounts().firstOrNull {
            normalizePhone(it.phoneNumber) == normalized
        }
    }

    fun findAccountByEmail(email: String): UserProfile? {
        val trimmed = email.trim()
        if (trimmed.isEmpty()) return null
        return getAllRegisteredAccounts().firstOrNull {
            it.email.equals(trimmed, ignoreCase = true)
        }
    }

    fun isPhoneRegistered(phone: String): Boolean {
        return findAccountByPhone(phone) != null
    }
}
