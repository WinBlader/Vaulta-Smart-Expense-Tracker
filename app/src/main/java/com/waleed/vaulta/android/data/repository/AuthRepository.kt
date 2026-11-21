package com.waleed.vaulta.android.data.repository

import com.waleed.vaulta.android.data.auth.PhoneNormalizationUtils
import com.waleed.vaulta.android.data.auth.supabase.SupabaseAuthService
import com.waleed.vaulta.android.data.auth.supabase.SupabaseUser
import com.waleed.vaulta.android.data.budget.BudgetManager
import com.waleed.vaulta.android.data.dao.ExpenseDao
import com.waleed.vaulta.android.data.model.UserProfile
import com.waleed.vaulta.android.data.session.UserSessionManager
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

interface AuthRepository {
    val currentUser: StateFlow<UserProfile?>
    val themeMode: StateFlow<String>
    val sessionStatus: Flow<SessionStatus>

    fun isLoggedIn(): Boolean
    suspend fun registerWithEmail(
        username: String,
        email: String,
        password: String,
        phone: String = ""
    ): Result<SupabaseUser>

    suspend fun registerWithPhone(
        username: String,
        phone: String,
        password: String
    ): Result<SupabaseUser>

    suspend fun registerWithPhone(
        username: String,
        countryCode: String,
        mobileNumber: String,
        password: String
    ): Result<SupabaseUser>

    suspend fun loginWithEmail(email: String, password: String): Result<SupabaseUser>
    suspend fun loginWithPhone(phone: String, password: String): Result<SupabaseUser>
    suspend fun loginWithPhone(countryCode: String, mobileNumber: String, password: String): Result<SupabaseUser>
    suspend fun loginWithGoogle(name: String, email: String, phone: String): Result<SupabaseUser>
    suspend fun sendEmailOtp(email: String): Result<String>
    suspend fun verifyEmailOtp(email: String, token: String): Result<SupabaseUser>
    suspend fun sendPhoneOtp(phone: String): Result<String>
    suspend fun verifyPhoneOtp(phone: String, token: String): Result<SupabaseUser>
    suspend fun loginWithGoogleIdToken(
        idToken: String,
        displayName: String = "",
        email: String = ""
    ): Result<SupabaseUser>

    suspend fun resetPasswordForEmail(email: String): Result<String>
    suspend fun signOut(): Result<Unit>

    suspend fun signInWithGoogleAccount(
        email: String,
        displayName: String? = null,
        phone: String? = null
    ): Result<SupabaseUser>

    suspend fun updateUserProfileCloud(name: String, email: String, phone: String): Result<Unit>

    fun updateUser(name: String, email: String, phone: String)
    fun loginAsGuest()
    fun setThemeMode(mode: String)
    fun logout()
    fun getAllRegisteredAccounts(): List<UserProfile>
}

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val supabaseAuthService: SupabaseAuthService,
    private val sessionManager: UserSessionManager,
    private val expenseDao: ExpenseDao,
    private val budgetManager: BudgetManager
) : AuthRepository {

    override val currentUser: StateFlow<UserProfile?> = sessionManager.currentUser
    override val themeMode: StateFlow<String> = sessionManager.themeMode
    override val sessionStatus: Flow<SessionStatus> = supabaseAuthService.sessionStatus

    override fun isLoggedIn(): Boolean = sessionManager.isLoggedIn()

    override suspend fun registerWithEmail(
        username: String,
        email: String,
        password: String,
        phone: String
    ): Result<SupabaseUser> {
        return supabaseAuthService.registerWithEmail(username, email, password, phone)
    }

    override suspend fun registerWithPhone(
        username: String,
        phone: String,
        password: String
    ): Result<SupabaseUser> {
        val trimmed = phone.trim()
        val digits = trimmed.filter { it.isDigit() }
        val (countryCode, local) = if (trimmed.startsWith("+91") && digits.length >= 12) {
            "+91" to digits.substring(2)
        } else if (trimmed.startsWith("+") && digits.length > 10) {
            val codeLen = digits.length - 10
            "+${digits.take(codeLen)}" to digits.drop(codeLen)
        } else {
            "+91" to digits
        }
        return registerWithPhone(username, countryCode, local, password)
    }

    override suspend fun registerWithPhone(
        username: String,
        countryCode: String,
        mobileNumber: String,
        password: String
    ): Result<SupabaseUser> {
        val normalized = PhoneNormalizationUtils.normalizePhone(countryCode, mobileNumber)
        return supabaseAuthService.registerWithEmail(
            username = username,
            email = normalized.loginEmail,
            password = password,
            phone = normalized.fullE164
        )
    }

    override suspend fun loginWithEmail(
        email: String,
        password: String
    ): Result<SupabaseUser> {
        return supabaseAuthService.loginWithEmail(email, password)
    }

    override suspend fun loginWithPhone(
        phone: String,
        password: String
    ): Result<SupabaseUser> {
        val trimmed = phone.trim()
        val digits = trimmed.filter { it.isDigit() }
        val (countryCode, local) = if (trimmed.startsWith("+91") && digits.length >= 12) {
            "+91" to digits.substring(2)
        } else if (trimmed.startsWith("+") && digits.length > 10) {
            val codeLen = digits.length - 10
            "+${digits.take(codeLen)}" to digits.drop(codeLen)
        } else {
            "+91" to digits
        }
        return loginWithPhone(countryCode, local, password)
    }

    override suspend fun loginWithPhone(
        countryCode: String,
        mobileNumber: String,
        password: String
    ): Result<SupabaseUser> {
        val normalized = PhoneNormalizationUtils.normalizePhone(countryCode, mobileNumber)
        val result = supabaseAuthService.loginWithEmail(normalized.loginEmail, password)
        return if (result.isSuccess) {
            result
        } else {
            // Fallback attempts for backward compatibility with previously stored formats
            val fallback1 = supabaseAuthService.loginWithEmail(normalized.fullE164, password)
            if (fallback1.isSuccess) {
                fallback1
            } else {
                val rawDigits = mobileNumber.filter { it.isDigit() }
                val fallback2 = supabaseAuthService.loginWithEmail("${rawDigits}@vaulta.app", password)
                if (fallback2.isSuccess) fallback2 else result
            }
        }
    }

    override suspend fun loginWithGoogle(
        name: String,
        email: String,
        phone: String
    ): Result<SupabaseUser> {
        return supabaseAuthService.signInWithGoogleAccount(
            email = email,
            displayName = name,
            phone = phone
        )
    }

    override suspend fun sendEmailOtp(email: String): Result<String> {
        return supabaseAuthService.sendEmailOtp(email)
    }

    override suspend fun verifyEmailOtp(email: String, token: String): Result<SupabaseUser> {
        return supabaseAuthService.verifyEmailOtp(email, token)
    }

    override suspend fun sendPhoneOtp(phone: String): Result<String> {
        return supabaseAuthService.sendPhoneOtp(phone)
    }

    override suspend fun verifyPhoneOtp(phone: String, token: String): Result<SupabaseUser> {
        return supabaseAuthService.verifyPhoneOtp(phone, token)
    }

    override suspend fun loginWithGoogleIdToken(
        idToken: String,
        displayName: String,
        email: String
    ): Result<SupabaseUser> {
        return supabaseAuthService.loginWithGoogleIdToken(idToken, displayName, email)
    }

    override suspend fun resetPasswordForEmail(email: String): Result<String> {
        return supabaseAuthService.resetPasswordForEmail(email)
    }

    override suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
        val userKey = sessionManager.getCurrentUserKey()
        try {
            // Defense-in-depth: Delete RoomDB cached records for this account on sign-out
            expenseDao.deleteExpensesForUser(userKey)
            budgetManager.resetBudgetForUser(userKey)
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "Error cleaning up local user data on sign out", e)
        }
        return@withContext supabaseAuthService.signOut()
    }

    override suspend fun signInWithGoogleAccount(
        email: String,
        displayName: String?,
        phone: String?
    ): Result<SupabaseUser> {
        return supabaseAuthService.signInWithGoogleAccount(email, displayName, phone)
    }

    override suspend fun updateUserProfileCloud(
        name: String,
        email: String,
        phone: String
    ): Result<Unit> {
        return supabaseAuthService.updateUserProfileCloud(name, email, phone)
    }

    override fun updateUser(name: String, email: String, phone: String) {
        sessionManager.updateUser(name, email, phone)
    }

    override fun loginAsGuest() {
        sessionManager.loginAsGuest()
    }

    override fun setThemeMode(mode: String) {
        sessionManager.setThemeMode(mode)
    }

    override fun logout() {
        val userKey = sessionManager.getCurrentUserKey()
        try {
            budgetManager.resetBudgetForUser(userKey)
        } catch (_: Exception) {}
        sessionManager.logout()
    }

    override fun getAllRegisteredAccounts(): List<UserProfile> {
        return sessionManager.getAllRegisteredAccounts()
    }
}
