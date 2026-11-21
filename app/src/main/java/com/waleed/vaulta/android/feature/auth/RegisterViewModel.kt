package com.waleed.vaulta.android.feature.auth

import android.util.Patterns
import androidx.lifecycle.viewModelScope
import com.waleed.vaulta.android.base.AuthNavigationEvent
import com.waleed.vaulta.android.base.BaseViewModel
import com.waleed.vaulta.android.base.UiEvent
import com.waleed.vaulta.android.data.auth.PhoneNormalizationUtils
import com.waleed.vaulta.android.data.model.UserProfile
import com.waleed.vaulta.android.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AuthTab {
    EMAIL,
    PHONE
}

data class RegisterUiState(
    val username: String = "",
    val email: String = "",
    val countryCode: String = "+91",
    val mobileNumber: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val authTab: AuthTab = AuthTab.EMAIL,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val showGoogleChooser: Boolean = false,
    val registeredAccounts: List<UserProfile> = emptyList()
)

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : BaseViewModel() {

    private val _uiState = MutableStateFlow(
        RegisterUiState(registeredAccounts = authRepository.getAllRegisteredAccounts())
    )
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    override fun onEvent(event: UiEvent) {
        when (event) {
            is RegisterUiEvent.OnUsernameChanged -> {
                _uiState.value = _uiState.value.copy(username = event.value, errorMessage = null)
            }

            is RegisterUiEvent.OnEmailChanged -> {
                _uiState.value = _uiState.value.copy(email = event.value, errorMessage = null)
            }

            is RegisterUiEvent.OnCountryCodeChanged -> {
                _uiState.value = _uiState.value.copy(countryCode = event.value, errorMessage = null)
            }

            is RegisterUiEvent.OnMobileChanged -> {
                _uiState.value = _uiState.value.copy(mobileNumber = event.value, errorMessage = null)
            }

            is RegisterUiEvent.OnPasswordChanged -> {
                _uiState.value = _uiState.value.copy(password = event.value, errorMessage = null)
            }

            is RegisterUiEvent.OnConfirmPasswordChanged -> {
                _uiState.value = _uiState.value.copy(confirmPassword = event.value, errorMessage = null)
            }

            is RegisterUiEvent.OnTabSelected -> {
                _uiState.value = _uiState.value.copy(authTab = event.tab, errorMessage = null)
            }

            is RegisterUiEvent.OnRegisterClicked -> {
                performSupabaseRegistration()
            }

            is RegisterUiEvent.OnGoogleSignInClicked -> {
                val accounts = authRepository.getAllRegisteredAccounts()
                _uiState.value = _uiState.value.copy(
                    registeredAccounts = accounts,
                    showGoogleChooser = true,
                    errorMessage = null
                )
            }

            is RegisterUiEvent.OnDismissGoogleChooser -> {
                _uiState.value = _uiState.value.copy(showGoogleChooser = false)
            }

            is RegisterUiEvent.OnGoogleAccountSelected -> {
                _uiState.value = _uiState.value.copy(showGoogleChooser = false, isLoading = true)
                viewModelScope.launch {
                    val result = authRepository.loginWithGoogle(
                        name = event.name,
                        email = event.email,
                        phone = event.phone
                    )
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    result.onSuccess {
                        _navigationEvent.emit(AuthNavigationEvent.NavigateToHome)
                    }.onFailure { err ->
                        _uiState.value = _uiState.value.copy(errorMessage = err.message ?: "Google Sign-in failed")
                    }
                }
            }

            is RegisterUiEvent.OnNavigateToLogin -> {
                viewModelScope.launch {
                    _navigationEvent.emit(AuthNavigationEvent.NavigateToLogin)
                }
            }

            is RegisterUiEvent.OnGuestClicked -> {
                authRepository.loginAsGuest()
                viewModelScope.launch {
                    _navigationEvent.emit(AuthNavigationEvent.NavigateToHome)
                }
            }
        }
    }

    private fun performSupabaseRegistration() {
        val state = _uiState.value
        val username = state.username.trim()
        val password = state.password
        val confirmPass = state.confirmPassword

        if (username.isEmpty()) {
            _uiState.value = state.copy(errorMessage = "Please enter a username")
            return
        }

        if (password.length < 6) {
            _uiState.value = state.copy(errorMessage = "Password must be at least 6 characters")
            return
        }

        if (password != confirmPass) {
            _uiState.value = state.copy(errorMessage = "Passwords do not match")
            return
        }

        if (state.authTab == AuthTab.EMAIL) {
            val email = state.email.trim()
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                _uiState.value = state.copy(errorMessage = "Please enter a valid email address")
                return
            }

            _uiState.value = state.copy(isLoading = true, errorMessage = null)
            viewModelScope.launch {
                val result = authRepository.registerWithEmail(
                    username = username,
                    email = email,
                    password = password,
                    phone = state.mobileNumber
                )
                _uiState.value = _uiState.value.copy(isLoading = false)
                result.onSuccess {
                    _navigationEvent.emit(AuthNavigationEvent.NavigateToHome)
                }.onFailure { err ->
                    _uiState.value = _uiState.value.copy(
                        errorMessage = err.message ?: "Registration failed with Supabase"
                    )
                }
            }
        } else {
            val validationError = PhoneNormalizationUtils.validatePhone(state.countryCode, state.mobileNumber)
            if (validationError != null) {
                _uiState.value = state.copy(errorMessage = validationError)
                return
            }

            _uiState.value = state.copy(isLoading = true, errorMessage = null)
            viewModelScope.launch {
                val result = authRepository.registerWithPhone(
                    username = username,
                    countryCode = state.countryCode,
                    mobileNumber = state.mobileNumber,
                    password = password
                )
                _uiState.value = _uiState.value.copy(isLoading = false)
                result.onSuccess {
                    _navigationEvent.emit(AuthNavigationEvent.NavigateToHome)
                }.onFailure { err ->
                    _uiState.value = _uiState.value.copy(
                        errorMessage = err.message ?: "Phone registration failed with Supabase"
                    )
                }
            }
        }
    }
}

sealed class RegisterUiEvent : UiEvent() {
    data class OnUsernameChanged(val value: String) : RegisterUiEvent()
    data class OnEmailChanged(val value: String) : RegisterUiEvent()
    data class OnCountryCodeChanged(val value: String) : RegisterUiEvent()
    data class OnMobileChanged(val value: String) : RegisterUiEvent()
    data class OnPasswordChanged(val value: String) : RegisterUiEvent()
    data class OnConfirmPasswordChanged(val value: String) : RegisterUiEvent()
    data class OnTabSelected(val tab: AuthTab) : RegisterUiEvent()
    data object OnRegisterClicked : RegisterUiEvent()
    data object OnGoogleSignInClicked : RegisterUiEvent()
    data object OnDismissGoogleChooser : RegisterUiEvent()
    data class OnGoogleAccountSelected(val name: String, val email: String, val phone: String) : RegisterUiEvent()
    data object OnNavigateToLogin : RegisterUiEvent()
    data object OnGuestClicked : RegisterUiEvent()
}
