package com.waleed.vaulta.android.data.auth.supabase

import android.util.Log
import com.waleed.vaulta.android.data.CurrencyManager
import com.waleed.vaulta.android.data.model.ProfileDto
import com.waleed.vaulta.android.data.model.UserProfile
import com.waleed.vaulta.android.data.session.UserSessionManager
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class SupabaseUser(
    val id: String,
    val username: String,
    val email: String,
    val phone: String,
    val provider: String,
    val accessToken: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Singleton
class SupabaseAuthService @Inject constructor(
    private val sessionManager: UserSessionManager
) {
    companion object {
        private const val TAG = "SupabaseAuthService"
        const val ERROR_NO_ACCOUNT = "No account found with these credentials. Please register first."
    }

    private val client = SupabaseClientProvider.client

    val sessionStatus: Flow<SessionStatus> = client.auth.sessionStatus

    /**
     * Awaits Supabase Auth initialization and ensures session is restored.
     * If session is null despite a signed-in local user, attempts refreshCurrentSession().
     */
    suspend fun ensureSessionInitialized(): String? = withContext(Dispatchers.IO) {
        if (!SupabaseClientProvider.isConfigured()) return@withContext null
        try {
            client.auth.awaitInitialization()
        } catch (e: Exception) {
            Log.e(TAG, "Failed awaiting Supabase auth initialization: ${e.message}", e)
        }

        var currentAuth = client.auth.currentUserOrNull()
        if (currentAuth == null && sessionManager.isLoggedIn()) {
            try {
                client.auth.refreshCurrentSession()
                currentAuth = client.auth.currentUserOrNull()
            } catch (e: Exception) {
                Log.e(TAG, "Failed refreshing Supabase session: ${e.message}", e)
            }
        }
        currentAuth?.id
    }

    /**
     * Signs up a new user via Supabase with Email, Password and full metadata.
     * Stored in both Supabase Auth user_metadata and the public profiles table.
     */
    suspend fun registerWithEmail(
        username: String,
        email: String,
        password: String,
        phone: String = ""
    ): Result<SupabaseUser> = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim()
        val cleanUsername = username.trim()
        val cleanPhone = phone.trim()

        if (!SupabaseClientProvider.isConfigured()) {
            return@withContext fallbackLocalRegistration(
                username = cleanUsername,
                email = cleanEmail,
                phone = cleanPhone,
                provider = "Supabase (Email)"
            )
        }

        try {
            client.auth.signUpWith(Email) {
                this.email = cleanEmail
                this.password = password
                this.data = buildJsonObject {
                    put("username", cleanUsername)
                    put("full_name", cleanUsername)
                    put("phone_number", cleanPhone)
                }
            }

            val currentUser = client.auth.currentUserOrNull()
            val userId = currentUser?.id ?: UUID.randomUUID().toString()

            val existingProfile: ProfileDto? = try {
                if (userId.isNotBlank()) {
                    client.from("profiles")
                        .select {
                            filter {
                                eq("id", userId)
                            }
                        }
                        .decodeSingleOrNull<ProfileDto>()
                } else null
            } catch (e: Exception) {
                Log.w(TAG, "Notice querying profiles in registerWithEmail: ${e.message}")
                null
            }

            val finalUsername = existingProfile?.displayName?.ifBlank { null }
                ?: existingProfile?.fullName?.ifBlank { null }
                ?: existingProfile?.username?.ifBlank { null }
                ?: cleanUsername

            val finalPhone = existingProfile?.cleanPhoneNumber?.ifBlank { null }
                ?: cleanPhone.takeIf { !it.equals("EMPTY", ignoreCase = true) }?.ifBlank { null }
                ?: ""

            // Persist profile to public.profiles table in Supabase Postgres
            val nowStr = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())
            val profile = if (existingProfile != null) {
                existingProfile.copy(
                    username = existingProfile.username?.ifBlank { null } ?: finalUsername,
                    displayName = existingProfile.displayName?.ifBlank { null } ?: finalUsername,
                    fullName = existingProfile.fullName?.ifBlank { null } ?: finalUsername,
                    email = existingProfile.email?.ifBlank { null } ?: cleanEmail,
                    phoneNumber = if (existingProfile.phoneNumber.isNullOrBlank() || existingProfile.phoneNumber.equals("EMPTY", ignoreCase = true)) {
                        finalPhone.ifBlank { null }
                    } else existingProfile.phoneNumber,
                    authProvider = existingProfile.authProvider ?: "Email",
                    updatedAt = nowStr
                )
            } else {
                ProfileDto(
                    id = userId,
                    username = finalUsername,
                    displayName = finalUsername,
                    fullName = finalUsername,
                    email = cleanEmail,
                    phoneNumber = finalPhone.ifBlank { null },
                    authProvider = "Email",
                    currencyCode = com.waleed.vaulta.android.data.CurrencyManager.getSelectedCurrency().code,
                    monthlyBudget = 0.0,
                    privacyPolicyAccepted = false,
                    updatedAt = nowStr
                )
            }

            try {
                client.from("profiles").upsert(profile)
            } catch (e: Exception) {
                Log.w(TAG, "Notice: could not upsert to public.profiles table (may be handled by trigger): ${e.message}")
            }

            existingProfile?.currencyCode?.takeIf { it.isNotBlank() }?.let {
                CurrencyManager.setCurrencyByCode(it, userId)
            }
            if (existingProfile?.privacyPolicyAccepted == true && userId.isNotBlank()) {
                sessionManager.setPrivacyPolicyAccepted(true, userId)
            }

            val user = SupabaseUser(
                id = userId,
                username = finalUsername,
                email = cleanEmail,
                phone = finalPhone,
                provider = "Email",
                accessToken = client.auth.currentAccessTokenOrNull() ?: ""
            )

            saveToSession(user)
            Result.success(user)
        } catch (e: Exception) {
            Log.e(TAG, "Error in registerWithEmail", e)
            val message = mapSupabaseErrorMessage(e.message ?: "")
            Result.failure(Exception(message))
        }
    }

    /**
     * Signs in an existing user with Email and Password.
     * Explicitly displays "No account found with these credentials. Please register first."
     * when the credentials do not match or the account does not exist.
     */
    suspend fun loginWithEmail(
        email: String,
        password: String
    ): Result<SupabaseUser> = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim()

        if (!SupabaseClientProvider.isConfigured()) {
            return@withContext fallbackLocalLogin(
                identifier = cleanEmail,
                password = password,
                isEmail = true
            )
        }

        try {
            client.auth.signInWith(Email) {
                this.email = cleanEmail
                this.password = password
            }

            val currentUser = client.auth.currentUserOrNull()
            val userId = currentUser?.id ?: ""

            // Retrieve profile from public profiles table
            var profile: ProfileDto? = null
            try {
                if (userId.isNotBlank()) {
                    profile = client.from("profiles")
                        .select {
                            filter {
                                eq("id", userId)
                            }
                        }
                        .decodeSingleOrNull<ProfileDto>()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not fetch from profiles table, using user_metadata fallback: ${e.message}")
            }

            val savedProfile = sessionManager.getSavedProfile(email = cleanEmail, id = userId)
            val username = profile?.username?.ifBlank { null }
                ?: profile?.fullName?.ifBlank { null }
                ?: savedProfile?.name?.ifBlank { null }
                ?: currentUser?.userMetadata?.get("username")?.toString()?.trim('"')
                ?: currentUser?.userMetadata?.get("full_name")?.toString()?.trim('"')
                ?: cleanEmail.substringBefore("@")

            val phone = profile?.cleanPhoneNumber?.ifBlank { null }
                ?: savedProfile?.phoneNumber?.takeIf { !it.equals("EMPTY", ignoreCase = true) }?.ifBlank { null }
                ?: currentUser?.userMetadata?.get("phone_number")?.toString()?.trim('"')?.takeIf { !it.equals("EMPTY", ignoreCase = true) }
                ?: currentUser?.phone?.takeIf { !it.equals("EMPTY", ignoreCase = true) }
                ?: ""

            profile?.currencyCode?.takeIf { it.isNotBlank() }?.let {
                CurrencyManager.setCurrencyByCode(it, userId)
            }
            if (profile?.privacyPolicyAccepted == true && userId.isNotBlank()) {
                sessionManager.setPrivacyPolicyAccepted(true, userId)
            }

            val user = SupabaseUser(
                id = userId,
                username = username,
                email = cleanEmail,
                phone = phone,
                provider = "Email",
                accessToken = client.auth.currentAccessTokenOrNull() ?: ""
            )

            saveToSession(user)
            Result.success(user)
        } catch (e: Exception) {
            Log.e(TAG, "Error in loginWithEmail", e)
            val mappedMsg = mapLoginErrorMessage(e.message ?: "")
            Result.failure(Exception(mappedMsg))
        }
    }

    /**
     * Passwordless / Magic Link or Email OTP sign-in request.
     */
    suspend fun sendEmailOtp(email: String): Result<String> = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim()
        if (!SupabaseClientProvider.isConfigured()) {
            return@withContext Result.success("A verification OTP has been sent to $cleanEmail (Demo Mode: use 123456)")
        }

        try {
            client.auth.signInWith(OTP) {
                this.email = cleanEmail
            }
            Result.success("Verification code sent to $cleanEmail. Please check your inbox.")
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Failed to send email OTP"))
        }
    }

    /**
     * Verifies Email OTP.
     */
    suspend fun verifyEmailOtp(email: String, token: String): Result<SupabaseUser> = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim()
        if (!SupabaseClientProvider.isConfigured()) {
            if (token.trim() == "123456") {
                return@withContext fallbackLocalLogin(cleanEmail, "", isEmail = true)
            } else {
                return@withContext Result.failure(Exception("Invalid OTP code. For demo mode, enter 123456."))
            }
        }

        try {
            client.auth.verifyEmailOtp(
                type = OtpType.Email.EMAIL,
                email = cleanEmail,
                token = token.trim()
            )
            val currentUser = client.auth.currentUserOrNull()
            val userId = currentUser?.id ?: ""
            val existingProfile: ProfileDto? = try {
                if (userId.isNotBlank()) {
                    client.from("profiles").select { filter { eq("id", userId) } }.decodeSingleOrNull<ProfileDto>()
                } else null
            } catch (_: Exception) { null }

            val resolvedUsername = existingProfile?.displayName?.ifBlank { null }
                ?: existingProfile?.fullName?.ifBlank { null }
                ?: existingProfile?.username?.ifBlank { null }
                ?: cleanEmail.substringBefore("@")

            val resolvedPhone = existingProfile?.cleanPhoneNumber ?: ""

            existingProfile?.currencyCode?.takeIf { it.isNotBlank() }?.let {
                CurrencyManager.setCurrencyByCode(it, userId)
            }
            if (existingProfile?.privacyPolicyAccepted == true && userId.isNotBlank()) {
                sessionManager.setPrivacyPolicyAccepted(true, userId)
            }

            val user = SupabaseUser(
                id = userId,
                username = resolvedUsername,
                email = cleanEmail,
                phone = resolvedPhone,
                provider = "Email OTP",
                accessToken = client.auth.currentAccessTokenOrNull() ?: ""
            )
            saveToSession(user)
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(Exception(mapLoginErrorMessage(e.message ?: "")))
        }
    }

    /**
     * Sends Phone OTP for mobile sign-in.
     */
    suspend fun sendPhoneOtp(phone: String): Result<String> = withContext(Dispatchers.IO) {
        val cleanPhone = phone.trim()
        if (!SupabaseClientProvider.isConfigured()) {
            return@withContext Result.success("Verification OTP sent to $cleanPhone (Demo Mode: use 123456)")
        }

        try {
            client.auth.signInWith(OTP) {
                this.phone = cleanPhone
            }
            Result.success("Verification code sent to $cleanPhone via SMS.")
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Failed to send phone OTP"))
        }
    }

    /**
     * Verifies Phone OTP.
     */
    suspend fun verifyPhoneOtp(phone: String, token: String): Result<SupabaseUser> = withContext(Dispatchers.IO) {
        val cleanPhone = phone.trim()
        if (!SupabaseClientProvider.isConfigured()) {
            if (token.trim() == "123456") {
                return@withContext fallbackLocalLogin(cleanPhone, "", isEmail = false)
            } else {
                return@withContext Result.failure(Exception("Invalid OTP code. For demo mode, enter 123456."))
            }
        }

        try {
            client.auth.verifyPhoneOtp(
                type = OtpType.Phone.SMS,
                phone = cleanPhone,
                token = token.trim()
            )
            val currentUser = client.auth.currentUserOrNull()
            val userId = currentUser?.id ?: ""
            val existingProfile: ProfileDto? = try {
                if (userId.isNotBlank()) {
                    client.from("profiles").select { filter { eq("id", userId) } }.decodeSingleOrNull<ProfileDto>()
                } else null
            } catch (_: Exception) { null }

            val resolvedUsername = existingProfile?.displayName?.ifBlank { null }
                ?: existingProfile?.fullName?.ifBlank { null }
                ?: existingProfile?.username?.ifBlank { null }
                ?: "User_${cleanPhone.takeLast(4)}"

            existingProfile?.currencyCode?.takeIf { it.isNotBlank() }?.let {
                CurrencyManager.setCurrencyByCode(it, userId)
            }
            if (existingProfile?.privacyPolicyAccepted == true && userId.isNotBlank()) {
                sessionManager.setPrivacyPolicyAccepted(true, userId)
            }

            val user = SupabaseUser(
                id = userId,
                username = resolvedUsername,
                email = existingProfile?.email ?: "",
                phone = cleanPhone,
                provider = "Phone OTP",
                accessToken = client.auth.currentAccessTokenOrNull() ?: ""
            )
            saveToSession(user)
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(Exception(mapLoginErrorMessage(e.message ?: "")))
        }
    }

    /**
     * Signs in via Google ID Token from AndroidX Credential Manager.
     */
    suspend fun loginWithGoogleIdToken(
        idToken: String,
        displayName: String = "",
        email: String = ""
    ): Result<SupabaseUser> = withContext(Dispatchers.IO) {
        if (!SupabaseClientProvider.isConfigured()) {
            val user = SupabaseUser(
                id = UUID.randomUUID().toString(),
                username = displayName.ifBlank { email.substringBefore("@") }.ifBlank { "Google User" },
                email = email,
                phone = "",
                provider = "Google"
            )
            saveToSession(user)
            return@withContext Result.success(user)
        }

        try {
            client.auth.signInWith(IDToken) {
                this.provider = Google
                this.idToken = idToken
            }

            val currentUser = client.auth.currentUserOrNull()
            val userId = currentUser?.id ?: UUID.randomUUID().toString()
            val savedProfile = sessionManager.getSavedProfile(email = email, id = userId)
            val resolvedEmail = email.ifBlank { currentUser?.email ?: "" }

            // Check if profile exists in Supabase public.profiles
            val existingProfile: ProfileDto? = try {
                if (userId.isNotBlank()) {
                    client.from("profiles")
                        .select {
                            filter {
                                eq("id", userId)
                            }
                        }
                        .decodeSingleOrNull<ProfileDto>()
                } else null
            } catch (e: Exception) {
                Log.w(TAG, "Notice querying profiles table in loginWithGoogleIdToken: ${e.message}")
                null
            }

            val finalDisplayName = existingProfile?.displayName?.ifBlank { null }
                ?: existingProfile?.fullName?.ifBlank { null }
                ?: existingProfile?.username?.ifBlank { null }
                ?: displayName.ifBlank { null }
                ?: savedProfile?.name?.ifBlank { null }
                ?: currentUser?.userMetadata?.get("full_name")?.toString()?.trim('"')
                ?: resolvedEmail.substringBefore("@")

            val finalPhone = existingProfile?.cleanPhoneNumber?.ifBlank { null }
                ?: savedProfile?.phoneNumber?.takeIf { !it.equals("EMPTY", ignoreCase = true) }?.ifBlank { null }
                ?: currentUser?.userMetadata?.get("phone_number")?.toString()?.trim('"')?.takeIf { !it.equals("EMPTY", ignoreCase = true) }
                ?: ""

            val nowStr = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())
            val profileToUpsert = if (existingProfile != null) {
                existingProfile.copy(
                    username = existingProfile.username?.ifBlank { null } ?: finalDisplayName,
                    displayName = existingProfile.displayName?.ifBlank { null } ?: finalDisplayName,
                    fullName = existingProfile.fullName?.ifBlank { null } ?: finalDisplayName,
                    email = existingProfile.email?.ifBlank { null } ?: resolvedEmail,
                    phoneNumber = if (existingProfile.phoneNumber.isNullOrBlank() || existingProfile.phoneNumber.equals("EMPTY", ignoreCase = true)) {
                        finalPhone.ifBlank { null }
                    } else existingProfile.phoneNumber,
                    authProvider = existingProfile.authProvider ?: "Google",
                    updatedAt = nowStr
                )
            } else {
                ProfileDto(
                    id = userId,
                    username = finalDisplayName,
                    displayName = finalDisplayName,
                    fullName = finalDisplayName,
                    email = resolvedEmail,
                    phoneNumber = finalPhone.ifBlank { null },
                    authProvider = "Google",
                    currencyCode = com.waleed.vaulta.android.data.CurrencyManager.getSelectedCurrency().code,
                    monthlyBudget = 0.0,
                    privacyPolicyAccepted = false,
                    updatedAt = nowStr
                )
            }

            try {
                client.from("profiles").upsert(profileToUpsert)
            } catch (e: Exception) {
                Log.w(TAG, "Notice upserting profile in loginWithGoogleIdToken: ${e.message}")
            }

            existingProfile?.currencyCode?.takeIf { it.isNotBlank() }?.let {
                CurrencyManager.setCurrencyByCode(it, userId)
            }
            if (existingProfile?.privacyPolicyAccepted == true && userId.isNotBlank()) {
                sessionManager.setPrivacyPolicyAccepted(true, userId)
            }

            val user = SupabaseUser(
                id = userId,
                username = finalDisplayName,
                email = resolvedEmail,
                phone = finalPhone,
                provider = "Google",
                accessToken = client.auth.currentAccessTokenOrNull() ?: ""
            )
            saveToSession(user)
            Result.success(user)
        } catch (e: Exception) {
            Log.e(TAG, "Error in loginWithGoogleIdToken", e)
            Result.failure(Exception(e.message ?: "Google authentication failed"))
        }
    }

    /**
     * Authenticates or registers a user via their selected device Google account.
     * Persists identity directly into Supabase Auth and public.profiles table.
     */
    suspend fun signInWithGoogleAccount(
        email: String,
        displayName: String? = null,
        phone: String? = null
    ): Result<SupabaseUser> = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank()) {
            return@withContext Result.failure(Exception("No Google account selected."))
        }

        val savedProfile = sessionManager.getSavedProfile(email = cleanEmail)
        val resolvedName = if (!displayName.isNullOrBlank()) {
            displayName.trim()
        } else if (!savedProfile?.name.isNullOrBlank()) {
            savedProfile!!.name
        } else {
            val localPart = cleanEmail.substringBefore("@")
            localPart.replace(".", " ").replace("_", " ")
                .split(" ")
                .filter { it.isNotBlank() }
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        }
        val cleanPhone = phone?.trim()?.ifBlank { null } ?: savedProfile?.phoneNumber ?: ""

        if (!SupabaseClientProvider.isConfigured()) {
            val user = SupabaseUser(
                id = UUID.randomUUID().toString(),
                username = resolvedName,
                email = cleanEmail,
                phone = cleanPhone,
                provider = "Google Account (Verified)"
            )
            sessionManager.saveUser(
                UserProfile(
                    name = user.username,
                    email = user.email,
                    phoneNumber = user.phone,
                    authProvider = "Google Account (Verified)",
                    isVerified = true
                )
            )
            return@withContext Result.success(user)
        }

        try {
            val googleSecret = "VaultaGoogle@2026#${cleanEmail.lowercase()}"
            var signedIn = false

            // Try logging in with the deterministic Google credentials
            try {
                client.auth.signInWith(Email) {
                    this.email = cleanEmail
                    this.password = googleSecret
                }
                signedIn = true
            } catch (e: Exception) {
                Log.d(TAG, "Notice on google signInWith: ${e.message}")
            }

            // If account is not registered yet, sign up
            if (!signedIn) {
                try {
                    client.auth.signUpWith(Email) {
                        this.email = cleanEmail
                        this.password = googleSecret
                        this.data = buildJsonObject {
                            put("username", resolvedName)
                            put("full_name", resolvedName)
                            put("provider", "Google")
                            put("phone_number", cleanPhone)
                        }
                    }
                    // Sign in to guarantee active session
                    client.auth.signInWith(Email) {
                        this.email = cleanEmail
                        this.password = googleSecret
                    }
                } catch (signUpErr: Exception) {
                    Log.w(TAG, "Notice on google signUpWith: ${signUpErr.message}")
                }
            }

            val currentUser = client.auth.currentUserOrNull()
            val userId = currentUser?.id ?: UUID.randomUUID().toString()

            val existingProfile: ProfileDto? = try {
                if (userId.isNotBlank()) {
                    client.from("profiles")
                        .select {
                            filter {
                                eq("id", userId)
                            }
                        }
                        .decodeSingleOrNull<ProfileDto>()
                } else null
            } catch (e: Exception) {
                Log.w(TAG, "Notice querying profiles in signInWithGoogleAccount: ${e.message}")
                null
            }

            val finalName = existingProfile?.displayName?.ifBlank { null }
                ?: existingProfile?.fullName?.ifBlank { null }
                ?: existingProfile?.username?.ifBlank { null }
                ?: resolvedName

            val finalPhone = existingProfile?.cleanPhoneNumber?.ifBlank { null }
                ?: cleanPhone.takeIf { !it.equals("EMPTY", ignoreCase = true) }?.ifBlank { null }
                ?: ""

            // Upsert into Supabase Postgres public.profiles table
            val nowStr = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())
            val profile = if (existingProfile != null) {
                existingProfile.copy(
                    username = existingProfile.username?.ifBlank { null } ?: finalName,
                    displayName = existingProfile.displayName?.ifBlank { null } ?: finalName,
                    fullName = existingProfile.fullName?.ifBlank { null } ?: finalName,
                    email = existingProfile.email?.ifBlank { null } ?: cleanEmail,
                    phoneNumber = if (existingProfile.phoneNumber.isNullOrBlank() || existingProfile.phoneNumber.equals("EMPTY", ignoreCase = true)) {
                        finalPhone.ifBlank { null }
                    } else existingProfile.phoneNumber,
                    authProvider = existingProfile.authProvider ?: "Google Account (Verified)",
                    updatedAt = nowStr
                )
            } else {
                ProfileDto(
                    id = userId,
                    username = finalName,
                    displayName = finalName,
                    fullName = finalName,
                    email = cleanEmail,
                    phoneNumber = finalPhone.ifBlank { null },
                    authProvider = "Google Account (Verified)",
                    currencyCode = com.waleed.vaulta.android.data.CurrencyManager.getSelectedCurrency().code,
                    monthlyBudget = 0.0,
                    privacyPolicyAccepted = false,
                    updatedAt = nowStr
                )
            }

            try {
                client.from("profiles").upsert(profile)
            } catch (e: Exception) {
                Log.w(TAG, "Notice: could not upsert to public.profiles: ${e.message}")
            }

            try {
                client.auth.updateUser {
                    data {
                        put("username", finalName)
                        put("full_name", finalName)
                        if (finalPhone.isNotBlank()) {
                            put("phone_number", finalPhone)
                        }
                        put("provider", "Google")
                    }
                }
            } catch (_: Exception) {}

            existingProfile?.currencyCode?.takeIf { it.isNotBlank() }?.let {
                CurrencyManager.setCurrencyByCode(it, userId)
            }
            if (existingProfile?.privacyPolicyAccepted == true && userId.isNotBlank()) {
                sessionManager.setPrivacyPolicyAccepted(true, userId)
            }

            val user = SupabaseUser(
                id = userId,
                username = finalName,
                email = cleanEmail,
                phone = finalPhone,
                provider = "Google Account (Verified)",
                accessToken = client.auth.currentAccessTokenOrNull() ?: ""
            )

            sessionManager.saveUser(
                UserProfile(
                    id = user.id,
                    name = user.username,
                    email = user.email,
                    phoneNumber = user.phone,
                    authProvider = "Google Account (Verified)",
                    isVerified = true
                )
            )

            Result.success(user)
        } catch (e: Exception) {
            Log.e(TAG, "Error in signInWithGoogleAccount", e)
            Result.failure(Exception(e.message ?: "Google Sign-In failed."))
        }
    }

    /**
     * Updates user profile in both local session and Supabase cloud (public.profiles & auth metadata).
     */
    suspend fun updateUserProfileCloud(
        name: String,
        email: String,
        phone: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanName = name.trim()
        val cleanEmail = email.trim()
        val cleanPhone = phone.trim()

        // 1. Update local session immediately
        sessionManager.updateUser(cleanName, cleanEmail, cleanPhone)

        if (!SupabaseClientProvider.isConfigured()) {
            return@withContext Result.success(Unit)
        }

        try {
            ensureSessionInitialized()
            val currentUser = client.auth.currentUserOrNull()
            val userId = currentUser?.id
            val currentSession = sessionManager.currentUser.value

            // 2. Update Supabase Auth user metadata
            try {
                client.auth.updateUser {
                    data {
                        if (cleanName.isNotBlank()) {
                            put("full_name", cleanName)
                            put("username", cleanName)
                        }
                        if (cleanPhone.isNotBlank()) {
                            put("phone_number", cleanPhone)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Notice updating Supabase auth metadata: ${e.message}")
            }

            // 3. Upsert to public.profiles table in Supabase Postgres
            if (!userId.isNullOrBlank()) {
                val nowStr = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())
                val existingProfile = try {
                    client.from("profiles").select {
                        filter { eq("id", userId) }
                    }.decodeSingleOrNull<ProfileDto>()
                } catch (e: Exception) {
                    null
                }

                val updatedProfile = ProfileDto(
                    id = userId,
                    username = cleanName.ifBlank { currentSession?.name ?: "" },
                    displayName = cleanName.ifBlank { currentSession?.name ?: "" },
                    fullName = cleanName.ifBlank { currentSession?.name ?: "" },
                    email = if (cleanEmail.isNotBlank()) cleanEmail else (existingProfile?.email ?: currentUser.email ?: ""),
                    phoneNumber = cleanPhone.ifBlank { null },
                    authProvider = existingProfile?.authProvider ?: currentSession?.authProvider ?: "Supabase Auth (Verified)",
                    avatarUrl = existingProfile?.avatarUrl,
                    currencyCode = existingProfile?.currencyCode ?: CurrencyManager.getSelectedCurrency().code,
                    monthlyBudget = existingProfile?.monthlyBudget,
                    initialBalance = existingProfile?.initialBalance ?: sessionManager.getInitialBalance(userId),
                    privacyPolicyAccepted = existingProfile?.privacyPolicyAccepted ?: sessionManager.isPrivacyPolicyAccepted(userId),
                    updatedAt = nowStr
                )
                client.from("profiles").upsert(updatedProfile)
                Log.d(TAG, "Successfully updated public.profiles for user: $userId")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating profile in Supabase: ${e.message}", e)
            Result.failure(Exception("Saved locally, cloud sync notice: ${e.message}"))
        }
    }

    /**
     * Sends password reset email.
     */
    suspend fun resetPasswordForEmail(email: String): Result<String> = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim()
        if (!SupabaseClientProvider.isConfigured()) {
            return@withContext Result.success("Password reset link has been dispatched to $cleanEmail")
        }

        try {
            client.auth.resetPasswordForEmail(
                email = cleanEmail,
                redirectUrl = "vaulta://login-callback"
            )
            Result.success("Password reset link sent to $cleanEmail. Please check your inbox.")
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Failed to send password reset email"))
        }
    }

    /**
     * Signs out the user, clearing all local and cloud session tokens.
     */
    suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (SupabaseClientProvider.isConfigured()) {
                client.auth.signOut()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Notice during signOut: ${e.message}")
        } finally {
            sessionManager.logout()
        }
        Result.success(Unit)
    }

    /**
     * Fetches fresh user profile from public.profiles table or user_metadata.
     */
    suspend fun fetchUserProfile(userId: String): ProfileDto? = withContext(Dispatchers.IO) {
        if (!SupabaseClientProvider.isConfigured() || userId.isBlank()) return@withContext null
        try {
            client.from("profiles").select {
                filter {
                    eq("id", userId)
                }
            }.decodeSingleOrNull<ProfileDto>()
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching user profile from profiles table: ${e.message}")
            null
        }
    }

    private fun mapLoginErrorMessage(rawMessage: String): String {
        val lower = rawMessage.lowercase()
        return when {
            lower.contains("invalid login credentials") ||
            lower.contains("invalid_credentials") ||
            lower.contains("user not found") ||
            lower.contains("no user found") ||
            lower.contains("user_not_found") -> {
                ERROR_NO_ACCOUNT
            }
            lower.contains("email not confirmed") -> {
                "Please verify your email address before signing in."
            }
            lower.contains("rate limit") -> {
                "Too many login attempts. Please wait a moment and try again."
            }
            else -> {
                if (rawMessage.isNotBlank()) rawMessage else ERROR_NO_ACCOUNT
            }
        }
    }

    private fun mapSupabaseErrorMessage(rawMessage: String): String {
        val lower = rawMessage.lowercase()
        return when {
            lower.contains("rate limit") || lower.contains("rate_limit") -> {
                "Email rate limit exceeded. In your Supabase Dashboard, go to Authentication -> Providers -> Email and turn OFF 'Confirm email'."
            }
            lower.contains("user already registered") || lower.contains("already exists") -> {
                "An account with this email/phone already exists. Please log in instead."
            }
            lower.contains("password should be at least") -> {
                "Password must be at least 6 characters long."
            }
            else -> if (rawMessage.isNotBlank()) rawMessage else "Registration failed"
        }
    }

    suspend fun updatePrivacyPolicyAcceptedCloud(userId: String? = null): Unit = withContext(Dispatchers.IO) {
        if (!SupabaseClientProvider.isConfigured()) return@withContext
        try {
            ensureSessionInitialized()
            val nowStr = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())
            val resolvedUserId = userId?.ifBlank { null }
                ?: client.auth.currentUserOrNull()?.id
                ?: sessionManager.currentUser.value?.id?.takeIf { it.isNotBlank() && it != "guest" }

            // 1. Immediately update public.profiles table in Supabase Postgres
            if (!resolvedUserId.isNullOrBlank() && resolvedUserId != "guest") {
                try {
                    client.from("profiles").update(mapOf("privacy_policy_accepted" to true)) {
                        filter {
                            eq("id", resolvedUserId)
                        }
                    }
                    Log.d(TAG, "Successfully updated privacy_policy_accepted = true in public.profiles for $resolvedUserId")
                } catch (pe: Exception) {
                    Log.w(TAG, "Notice updating privacy policy in public.profiles: ${pe.message}")
                }
            }

            // 2. Also update auth user metadata
            try {
                client.auth.updateUser {
                    data {
                        put("privacy_policy_accepted_v1", true)
                        put("privacy_policy_accepted_at", nowStr)
                    }
                }
            } catch (ae: Exception) {
                Log.w(TAG, "Notice updating auth metadata: ${ae.message}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Notice updating privacy policy in Supabase: ${e.message}")
        }
    }

    suspend fun updateInitialBalanceCloud(initialBalance: Double, userId: String? = null): Unit = withContext(Dispatchers.IO) {
        if (!SupabaseClientProvider.isConfigured()) return@withContext
        try {
            ensureSessionInitialized()
            val resolvedUserId = userId?.ifBlank { null }
                ?: client.auth.currentUserOrNull()?.id
                ?: sessionManager.currentUser.value?.id?.takeIf { it.isNotBlank() && it != "guest" }

            if (!resolvedUserId.isNullOrBlank() && resolvedUserId != "guest") {
                try {
                    client.from("profiles").update(mapOf("initial_balance" to initialBalance)) {
                        filter {
                            eq("id", resolvedUserId)
                        }
                    }
                    Log.d(TAG, "Successfully updated initial_balance = $initialBalance in public.profiles for $resolvedUserId")
                } catch (pe: Exception) {
                    Log.w(TAG, "Notice updating initial_balance in public.profiles: ${pe.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed updating initial balance in cloud: ${e.message}", e)
        }
    }

    suspend fun updateBalancesCloud(initialBalance: Double, currentBalance: Double, userId: String? = null): Unit = withContext(Dispatchers.IO) {
        if (!SupabaseClientProvider.isConfigured()) return@withContext
        try {
            ensureSessionInitialized()
            val resolvedUserId = userId?.ifBlank { null }
                ?: client.auth.currentUserOrNull()?.id
                ?: sessionManager.currentUser.value?.id?.takeIf { it.isNotBlank() && it != "guest" }

            if (!resolvedUserId.isNullOrBlank() && resolvedUserId != "guest") {
                try {
                    client.from("profiles").update(
                        mapOf(
                            "initial_balance" to initialBalance,
                            "current_balance" to currentBalance
                        )
                    ) {
                        filter {
                            eq("id", resolvedUserId)
                        }
                    }
                    Log.d(TAG, "Successfully updated balances (initial=$initialBalance, current=$currentBalance) in public.profiles for $resolvedUserId")
                } catch (pe: Exception) {
                    Log.w(TAG, "Notice updating balances in public.profiles: ${pe.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed updating balances in cloud: ${e.message}", e)
        }
    }

    /**
     * Looks up a registered Vaulta user by email or mobile number using secure RPC lookup_vaulta_contact.
     * Returns found = false if offline, guest, or not found.
     */
    suspend fun lookupVaultaContact(query: String): com.waleed.vaulta.android.data.model.ContactLookupResult = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank() || !SupabaseClientProvider.isConfigured() || !sessionManager.isLoggedIn()) {
            return@withContext com.waleed.vaulta.android.data.model.ContactLookupResult(found = false)
        }
        try {
            ensureSessionInitialized()
            val rpcParams = buildJsonObject {
                put("p_query", cleanQuery)
            }
            val result = client.postgrest.rpc("lookup_vaulta_contact", rpcParams).decodeAs<com.waleed.vaulta.android.data.model.ContactLookupResult>()
            result
        } catch (e: Exception) {
            Log.w(TAG, "Notice during contact lookup for '$cleanQuery': ${e.message}")
            com.waleed.vaulta.android.data.model.ContactLookupResult(found = false)
        }
    }

    private fun saveToSession(user: SupabaseUser) {
        sessionManager.saveUser(
            UserProfile(
                id = user.id,
                name = user.username,
                email = user.email,
                phoneNumber = user.phone,
                authProvider = if (user.provider.startsWith("Supabase")) user.provider else "Supabase (${user.provider})"
            )
        )
        try {
            val currentUser = client.auth.currentUserOrNull()
            val metaAccepted = currentUser?.userMetadata?.get("privacy_policy_accepted_v1")?.toString()?.trim('"')?.toBooleanStrictOrNull()
                ?: currentUser?.userMetadata?.get("privacy_policy_accepted")?.toString()?.trim('"')?.toBooleanStrictOrNull()
                ?: false
            if (metaAccepted && user.id.isNotBlank()) {
                sessionManager.setPrivacyPolicyAccepted(true, user.id)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not check privacy policy metadata: ${e.message}")
        }
    }

    private fun fallbackLocalRegistration(
        username: String,
        email: String,
        phone: String,
        provider: String
    ): Result<SupabaseUser> {
        val allUsers = sessionManager.getAllRegisteredAccounts()
        val exists = allUsers.any {
            (email.isNotBlank() && it.email.equals(email, ignoreCase = true)) ||
            (phone.isNotBlank() && it.phoneNumber == phone)
        }
        if (exists) {
            return Result.failure(Exception("An account with these details already exists. Please log in."))
        }
        val user = SupabaseUser(
            id = UUID.randomUUID().toString(),
            username = username,
            email = email,
            phone = phone,
            provider = provider
        )
        saveToSession(user)
        return Result.success(user)
    }

    private fun fallbackLocalLogin(
        identifier: String,
        password: String,
        isEmail: Boolean
    ): Result<SupabaseUser> {
        val allUsers = sessionManager.getAllRegisteredAccounts()
        val found = allUsers.firstOrNull {
            if (isEmail) {
                it.email.equals(identifier, ignoreCase = true)
            } else {
                it.phoneNumber == identifier
            }
        }

        return if (found != null) {
            val user = SupabaseUser(
                id = UUID.randomUUID().toString(),
                username = found.name,
                email = found.email,
                phone = found.phoneNumber,
                provider = found.authProvider
            )
            saveToSession(user)
            Result.success(user)
        } else {
            Result.failure(Exception(ERROR_NO_ACCOUNT))
        }
    }
}
