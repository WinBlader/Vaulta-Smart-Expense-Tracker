package com.waleed.vaulta.android.feature.auth

import androidx.lifecycle.viewModelScope
import com.waleed.vaulta.android.base.AuthNavigationEvent
import com.waleed.vaulta.android.base.BaseViewModel
import com.waleed.vaulta.android.base.UiEvent
import com.waleed.vaulta.android.data.model.UserProfile
import com.waleed.vaulta.android.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val identifier: String = "", // Email or Mobile Number
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val showGoogleChooser: Boolean = false,
    val registeredAccounts: List<UserProfile> = emptyList()
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : BaseViewModel() {

    private val _uiState = MutableStateFlow(
        LoginUiState(registeredAccounts = authRepository.getAllRegisteredAccounts())
    )
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    override fun onEvent(event: UiEvent) {
        when (event) {
            is LoginUiEvent.OnIdentifierChanged -> {
                _uiState.value = _uiState.value.copy(identifier = event.value, errorMessage = null)
            }

            is LoginUiEvent.OnPasswordChanged -> {
                _uiState.value = _uiState.value.copy(password = event.value, errorMessage = null)
            }

            is LoginUiEvent.OnLoginClicked -> {
                performSupabaseLogin()
            }

            is LoginUiEvent.OnGoogleSignInClicked -> {
                val accounts = authRepository.getAllRegisteredAccounts()
                _uiState.value = _uiState.value.copy(
                    registeredAccounts = accounts,
                    showGoogleChooser = true,
                    errorMessage = null
                )
            }

            is LoginUiEvent.OnDismissGoogleChooser -> {
                _uiState.value = _uiState.value.copy(showGoogleChooser = false)
            }

            is LoginUiEvent.OnGoogleAccountSelected -> {
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

            is LoginUiEvent.OnNavigateToRegister -> {
                viewModelScope.launch {
                    _navigationEvent.emit(AuthNavigationEvent.NavigateToRegister)
                }
            }

            is LoginUiEvent.OnGuestClicked -> {
                authRepository.loginAsGuest()
                viewModelScope.launch {
                    _navigationEvent.emit(AuthNavigationEvent.NavigateToHome)
                }
            }
        }
    }

    private fun performSupabaseLogin() {
        val state = _uiState.value
        val input = state.identifier.trim()
        val password = state.password

        if (input.isEmpty()) {
            _uiState.value = state.copy(errorMessage = "Please enter your Email or Mobile number")
            return
        }

        if (password.isEmpty()) {
            _uiState.value = state.copy(errorMessage = "Please enter your password")
            return
        }

        _uiState.value = state.copy(isLoading = true, errorMessage = null)

        val isPhone = input.all { it.isDigit() || it == '+' } && input.filter { it.isDigit() }.length >= 10

        viewModelScope.launch {
            val result = if (isPhone) {
                authRepository.loginWithPhone(input, password)
            } else {
                authRepository.loginWithEmail(input, password)
            }

            _uiState.value = _uiState.value.copy(isLoading = false)

            result.onSuccess {
                _navigationEvent.emit(AuthNavigationEvent.NavigateToHome)
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(
                    errorMessage = err.message ?: "Login failed. Check your credentials."
                )
            }
        }
    }
}

sealed class LoginUiEvent : UiEvent() {
    data class OnIdentifierChanged(val value: String) : LoginUiEvent()
    data class OnPasswordChanged(val value: String) : LoginUiEvent()
    data object OnLoginClicked : LoginUiEvent()
    data object OnGoogleSignInClicked : LoginUiEvent()
    data object OnDismissGoogleChooser : LoginUiEvent()
    data class OnGoogleAccountSelected(val name: String, val email: String, val phone: String) : LoginUiEvent()
    data object OnNavigateToRegister : LoginUiEvent()
    data object OnGuestClicked : LoginUiEvent()
}
