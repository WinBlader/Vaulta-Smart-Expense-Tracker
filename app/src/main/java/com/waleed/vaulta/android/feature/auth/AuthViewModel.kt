package com.waleed.vaulta.android.feature.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.waleed.vaulta.android.BuildConfig
import com.waleed.vaulta.android.data.auth.PhoneNormalizationUtils
import com.waleed.vaulta.android.data.auth.supabase.SupabaseUser
import com.waleed.vaulta.android.data.model.UserProfile
import com.waleed.vaulta.android.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AuthUiEffect {
    data object NavigateToHome : AuthUiEffect()
    data object NavigateToLogin : AuthUiEffect()
    data object NavigateToRegister : AuthUiEffect()
    data class ShowToast(val message: String) : AuthUiEffect()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    val currentUser: StateFlow<UserProfile?> = authRepository.currentUser
    val sessionStatus = authRepository.sessionStatus

    private val _uiEffect = MutableSharedFlow<AuthUiEffect>()
    val uiEffect: SharedFlow<AuthUiEffect> = _uiEffect.asSharedFlow()

    fun clearMessages() {
        _errorMessage.value = null
        _successMessage.value = null
    }

    /**
     * (1) Email/Password Sign-Up with full metadata and cloud persistence in profiles table.
     */
    fun signUpWithEmail(
        username: String,
        email: String,
        password: String,
        phone: String = ""
    ) {
        if (username.isBlank() || email.isBlank() || password.isBlank()) {
            _errorMessage.value = "Please fill in all required fields."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _successMessage.value = null

            val result = authRepository.registerWithEmail(
                username = username,
                email = email,
                password = password,
                phone = phone
            )

            _isLoading.value = false
            result.onSuccess {
                _successMessage.value = "Account created successfully! Welcome to Vaulta."
                _uiEffect.emit(AuthUiEffect.NavigateToHome)
            }.onFailure { error ->
                _errorMessage.value = error.message ?: "Sign-up failed. Please try again."
            }
        }
    }

    /**
     * (2) Email/Password Sign-In with strict validation and clear "No account found" message.
     */
    fun signInWithEmail(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _errorMessage.value = "Please enter both email and password."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _successMessage.value = null

            val result = authRepository.loginWithEmail(email, password)
            _isLoading.value = false

            result.onSuccess {
                _successMessage.value = "Logged in successfully!"
                _uiEffect.emit(AuthUiEffect.NavigateToHome)
            }.onFailure { error ->
                _errorMessage.value = error.message ?: "Login failed. Please check your credentials."
            }
        }
    }

    /**
     * (2b) Mobile Phone Sign-In with Country Code and Password.
     */
    fun signInWithPhone(countryCode: String, mobileNumber: String, password: String) {
        val phoneError = PhoneNormalizationUtils.validatePhone(countryCode, mobileNumber)
        if (phoneError != null) {
            _errorMessage.value = phoneError
            return
        }
        if (password.isBlank()) {
            _errorMessage.value = "Please enter your password."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _successMessage.value = null

            val result = authRepository.loginWithPhone(countryCode, mobileNumber, password)
            _isLoading.value = false

            result.onSuccess {
                _successMessage.value = "Logged in successfully!"
                _uiEffect.emit(AuthUiEffect.NavigateToHome)
            }.onFailure { error ->
                _errorMessage.value = error.message ?: "Login failed. Please check your credentials."
            }
        }
    }

    /**
     * (3) Magic Link / Email OTP sign-in request.
     */
    fun sendEmailOtp(email: String) {
        if (email.isBlank()) {
            _errorMessage.value = "Please enter your email address."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val result = authRepository.sendEmailOtp(email)
            _isLoading.value = false
            result.onSuccess { msg ->
                _successMessage.value = msg
            }.onFailure { err ->
                _errorMessage.value = err.message ?: "Failed to send verification email."
            }
        }
    }

    fun verifyEmailOtp(email: String, token: String) {
        if (token.isBlank()) {
            _errorMessage.value = "Please enter the verification code."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val result = authRepository.verifyEmailOtp(email, token)
            _isLoading.value = false
            result.onSuccess {
                _successMessage.value = "Email verified successfully!"
                _uiEffect.emit(AuthUiEffect.NavigateToHome)
            }.onFailure { err ->
                _errorMessage.value = err.message ?: "Verification failed."
            }
        }
    }

    /**
     * (3) Phone OTP request & verification.
     */
    fun sendPhoneOtp(phone: String) {
        if (phone.isBlank()) {
            _errorMessage.value = "Please enter your mobile number."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val result = authRepository.sendPhoneOtp(phone)
            _isLoading.value = false
            result.onSuccess { msg ->
                _successMessage.value = msg
            }.onFailure { err ->
                _errorMessage.value = err.message ?: "Failed to send OTP."
            }
        }
    }

    fun verifyPhoneOtp(phone: String, token: String) {
        if (token.isBlank()) {
            _errorMessage.value = "Please enter the OTP."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val result = authRepository.verifyPhoneOtp(phone, token)
            _isLoading.value = false
            result.onSuccess {
                _successMessage.value = "Mobile number verified!"
                _uiEffect.emit(AuthUiEffect.NavigateToHome)
            }.onFailure { err ->
                _errorMessage.value = err.message ?: "Invalid OTP."
            }
        }
    }

    /**
     * (3) Google Sign-In with selected Google Account.
     * Registers and authenticates the chosen Google account in Supabase.
     */
    fun signInWithGoogleAccount(
        email: String,
        displayName: String? = null,
        phone: String? = null
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val result = authRepository.signInWithGoogleAccount(email, displayName, phone)
            _isLoading.value = false
            result.onSuccess { user ->
                _successMessage.value = "Signed in as ${user.username} with Google!"
                _uiEffect.emit(AuthUiEffect.NavigateToHome)
            }.onFailure { err ->
                _errorMessage.value = err.message ?: "Google authentication failed."
            }
        }
    }

    fun getAllRegisteredAccounts(): List<UserProfile> = authRepository.getAllRegisteredAccounts()

    fun loginAsGuest() {
        authRepository.loginAsGuest()
        viewModelScope.launch {
            _successMessage.value = "Welcome! Exploring Vaulta as Guest"
            _uiEffect.emit(AuthUiEffect.NavigateToHome)
        }
    }

    /**
     * Native Google Sign-In via AndroidX Credential Manager.
     */
    fun launchGoogleSignIn(context: Context) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val credentialManager = CredentialManager.create(context)
                val clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID.ifBlank { "dummy-client-id" }

                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(clientId)
                    .setAutoSelectEnabled(false)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(context = context, request = request)
                val credential = result.credential

                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val idToken = googleIdTokenCredential.idToken
                    val displayName = googleIdTokenCredential.displayName ?: ""
                    val email = googleIdTokenCredential.id

                    val authResult = authRepository.loginWithGoogleIdToken(
                        idToken = idToken,
                        displayName = displayName,
                        email = email
                    )

                    _isLoading.value = false
                    authResult.onSuccess {
                        _successMessage.value = "Welcome, ${it.username}!"
                        _uiEffect.emit(AuthUiEffect.NavigateToHome)
                    }.onFailure { err ->
                        _errorMessage.value = err.message ?: "Google authentication failed."
                    }
                } else {
                    _isLoading.value = false
                    _errorMessage.value = "Unsupported credential returned."
                }
            } catch (e: Exception) {
                _isLoading.value = false
                Log.w("AuthViewModel", "Google Credential Manager: ${e.message}")
                _errorMessage.value = "Google account picker cancelled or unavailable."
            }
        }
    }

    /**
     * (3) Password Reset via Email.
     */
    fun resetPasswordForEmail(email: String) {
        if (email.isBlank()) {
            _errorMessage.value = "Please enter your email to reset password."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val result = authRepository.resetPasswordForEmail(email)
            _isLoading.value = false
            result.onSuccess { msg ->
                _successMessage.value = msg
            }.onFailure { err ->
                _errorMessage.value = err.message ?: "Failed to send reset link."
            }
        }
    }

    /**
     * (4) Profile Sign-Out. Clears all session tokens and navigates to Login.
     */
    fun signOut() {
        viewModelScope.launch {
            _isLoading.value = true
            authRepository.signOut()
            _isLoading.value = false
            _uiEffect.emit(AuthUiEffect.NavigateToLogin)
        }
    }
}
