package com.waleed.vaulta.android.feature.profile

import androidx.lifecycle.viewModelScope
import com.waleed.vaulta.android.base.BaseViewModel
import com.waleed.vaulta.android.base.ProfileNavigationEvent
import com.waleed.vaulta.android.base.UiEvent
import com.waleed.vaulta.android.data.CurrencyManager
import com.waleed.vaulta.android.data.model.CurrencyItem
import com.waleed.vaulta.android.data.model.UserProfile
import com.waleed.vaulta.android.data.repository.AuthRepository
import com.waleed.vaulta.android.data.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val isEditing: Boolean = false,
    val editName: String = "",
    val editEmail: String = "",
    val editPhone: String = "",
    val showLogoutConfirm: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val expenseRepository: ExpenseRepository
) : BaseViewModel() {

    val user: StateFlow<UserProfile?> = authRepository.currentUser
    val themeMode: StateFlow<String> = authRepository.themeMode
    val selectedCurrency: StateFlow<CurrencyItem> = CurrencyManager.selectedCurrency

    val expenses = expenseRepository.getAllExpenses().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    fun onCurrencySelected(currency: CurrencyItem) {
        CurrencyManager.setCurrency(currency)
    }

    override fun onEvent(event: UiEvent) {
        when (event) {
            is ProfileUiEvent.OnStartEdit -> {
                val currentUser = user.value
                _uiState.value = _uiState.value.copy(
                    isEditing = true,
                    editName = currentUser?.name ?: "",
                    editEmail = currentUser?.email ?: "",
                    editPhone = currentUser?.phoneNumber ?: "",
                    message = null
                )
            }

            is ProfileUiEvent.OnNameChanged -> {
                _uiState.value = _uiState.value.copy(editName = event.value)
            }

            is ProfileUiEvent.OnEmailChanged -> {
                _uiState.value = _uiState.value.copy(editEmail = event.value)
            }

            is ProfileUiEvent.OnPhoneChanged -> {
                _uiState.value = _uiState.value.copy(editPhone = event.value)
            }

            is ProfileUiEvent.OnSaveEdit -> {
                val state = _uiState.value
                val newName = state.editName.trim()
                val newEmail = state.editEmail.trim()
                val newPhone = state.editPhone.trim()

                _uiState.value = _uiState.value.copy(
                    isEditing = false,
                    message = "Updating profile in Supabase..."
                )

                viewModelScope.launch {
                    val result = authRepository.updateUserProfileCloud(
                        name = newName,
                        email = newEmail,
                        phone = newPhone
                    )
                    result.onSuccess {
                        _uiState.value = _uiState.value.copy(
                            message = "Profile successfully updated in Supabase!"
                        )
                    }.onFailure { err ->
                        _uiState.value = _uiState.value.copy(
                            message = "Profile updated locally. Cloud: ${err.message}"
                        )
                    }
                }
            }

            is ProfileUiEvent.OnCancelEdit -> {
                _uiState.value = _uiState.value.copy(isEditing = false)
            }

            is ProfileUiEvent.OnThemeModeChanged -> {
                authRepository.setThemeMode(event.mode)
            }

            is ProfileUiEvent.OnThemeChanged -> {
                authRepository.setThemeMode(event.mode)
            }

            is ProfileUiEvent.OnCurrencySelected -> {
                CurrencyManager.setCurrency(event.currency)
            }

            is ProfileUiEvent.OnRequestLogout -> {
                _uiState.value = _uiState.value.copy(showLogoutConfirm = true)
            }

            is ProfileUiEvent.OnLogoutClicked -> {
                _uiState.value = _uiState.value.copy(showLogoutConfirm = true)
            }

            is ProfileUiEvent.OnDismissLogout -> {
                _uiState.value = _uiState.value.copy(showLogoutConfirm = false)
            }

            is ProfileUiEvent.OnConfirmLogout -> {
                _uiState.value = _uiState.value.copy(showLogoutConfirm = false)
                viewModelScope.launch {
                    try {
                        authRepository.signOut()
                    } catch (e: Exception) {
                        android.util.Log.e("ProfileViewModel", "Sign out error", e)
                    }
                    com.waleed.vaulta.android.utils.VaultaToast.show(message = "Logged out successfully.")
                    _navigationEvent.emit(ProfileNavigationEvent.NavigateToLogin)
                }
            }
        }
    }
}

sealed class ProfileUiEvent : UiEvent() {
    data object OnStartEdit : ProfileUiEvent()
    data class OnNameChanged(val value: String) : ProfileUiEvent()
    data class OnEmailChanged(val value: String) : ProfileUiEvent()
    data class OnPhoneChanged(val value: String) : ProfileUiEvent()
    data object OnSaveEdit : ProfileUiEvent()
    data object OnCancelEdit : ProfileUiEvent()
    data class OnThemeModeChanged(val mode: String) : ProfileUiEvent()
    data class OnThemeChanged(val mode: String) : ProfileUiEvent()
    data class OnCurrencySelected(val currency: CurrencyItem) : ProfileUiEvent()
    data object OnRequestLogout : ProfileUiEvent()
    data object OnLogoutClicked : ProfileUiEvent()
    data object OnDismissLogout : ProfileUiEvent()
    data object OnConfirmLogout : ProfileUiEvent()
}
