package com.waleed.vaulta.android.data.repository

import com.waleed.vaulta.android.data.auth.supabase.SupabaseAuthService
import com.waleed.vaulta.android.data.auth.supabase.SupabaseUser
import com.waleed.vaulta.android.data.model.UserProfile
import com.waleed.vaulta.android.data.session.UserSessionManager
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
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

    suspend fun loginWithEmail(email: String, password: String): Result<SupabaseUser>
    suspend fun loginWithPhone(phone: String, password: String): Result<SupabaseUser>
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
    private val sessionManager: UserSessionManager
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
        val fakeEmail = "${phone.filter { it.isDigit() }}@vaulta.app"
        return supabaseAuthService.registerWithEmail(
            username = username,
            email = fakeEmail,
            password = password,
            phone = phone
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
        val fakeEmail = "${phone.filter { it.isDigit() }}@vaulta.app"
        val result = supabaseAuthService.loginWithEmail(fakeEmail, password)
        return if (result.isSuccess) {
            result
        } else {
            supabaseAuthService.loginWithEmail(phone, password)
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

    override suspend fun signOut(): Result<Unit> {
        return supabaseAuthService.signOut()
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
        sessionManager.logout()
    }

    override fun getAllRegisteredAccounts(): List<UserProfile> {
        return sessionManager.getAllRegisteredAccounts()
    }
}
