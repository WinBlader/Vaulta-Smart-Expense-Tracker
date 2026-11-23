package com.waleed.vaulta.android.feature.profile

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.waleed.vaulta.android.base.BaseViewModel
import com.waleed.vaulta.android.base.ProfileNavigationEvent
import com.waleed.vaulta.android.base.UiEvent
import com.waleed.vaulta.android.data.CurrencyManager
import com.waleed.vaulta.android.data.model.CurrencyItem
import com.waleed.vaulta.android.data.model.UserProfile
import com.waleed.vaulta.android.data.notification.NotificationAccessHelper
import com.waleed.vaulta.android.data.repository.AuthRepository
import com.waleed.vaulta.android.data.repository.BudgetRepository
import com.waleed.vaulta.android.data.repository.ExpenseRepository
import com.waleed.vaulta.android.data.session.UserSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    val showAutoDetectDisclosureDialog: Boolean = false,
    val showResetEverythingDialog: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val expenseRepository: ExpenseRepository,
    private val budgetRepository: BudgetRepository,
    private val sessionManager: UserSessionManager,
    @ApplicationContext private val context: Context
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

    private var pendingAutoDetectEnable = false

    private val _isNotificationAccessGranted = MutableStateFlow(
        NotificationAccessHelper.isNotificationListenerEnabled(context)
    )
    val isNotificationAccessGranted: StateFlow<Boolean> = _isNotificationAccessGranted.asStateFlow()

    private val _isServiceConnected = MutableStateFlow(
        NotificationAccessHelper.isServiceConnected()
    )
    val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

    private val _isAutoDetectEnabled = MutableStateFlow(
        NotificationAccessHelper.isNotificationListenerEnabled(context) &&
                NotificationAccessHelper.isAutoDetectEnabled(context, sessionManager.getCurrentUserKey())
    )
    val isAutoDetectEnabled: StateFlow<Boolean> = _isAutoDetectEnabled.asStateFlow()

    private val _isIgnoringBatteryOptimizations = MutableStateFlow(
        NotificationAccessHelper.isIgnoringBatteryOptimizations(context)
    )
    val isIgnoringBatteryOptimizations: StateFlow<Boolean> = _isIgnoringBatteryOptimizations.asStateFlow()

    init {
        refreshNotificationAccess()
    }

    fun refreshNotificationAccess() {
        val userKey = sessionManager.getCurrentUserKey()
        val granted = NotificationAccessHelper.isNotificationListenerEnabled(context)
        _isNotificationAccessGranted.value = granted
        _isIgnoringBatteryOptimizations.value = NotificationAccessHelper.isIgnoringBatteryOptimizations(context)

        if (granted) {
            NotificationAccessHelper.checkAndRequestRebind(context)
            _isServiceConnected.value = NotificationAccessHelper.isServiceConnected()

            // If user granted permission in settings and returned (or had it previously enabled)
            if (pendingAutoDetectEnable || NotificationAccessHelper.isAutoDetectEnabled(context, userKey)) {
                NotificationAccessHelper.setAutoDetectEnabled(context, true, userKey)
                _isAutoDetectEnabled.value = true
                pendingAutoDetectEnable = false
            }
        } else {
            // Permission was revoked or not yet granted
            NotificationAccessHelper.setAutoDetectEnabled(context, false, userKey)
            _isAutoDetectEnabled.value = false
            _isServiceConnected.value = false
        }
    }

    fun onCurrencySelected(currency: CurrencyItem) {
        CurrencyManager.setCurrency(currency)
    }

    override fun onEvent(event: UiEvent) {
        when (event) {
            is ProfileUiEvent.OnResume -> {
                refreshNotificationAccess()
            }

            is ProfileUiEvent.OnToggleAutoDetect -> {
                val userKey = sessionManager.getCurrentUserKey()
                if (event.enabled) {
                    val hasAccess = NotificationAccessHelper.isNotificationListenerEnabled(context)
                    if (!hasAccess) {
                        pendingAutoDetectEnable = true
                        _uiState.value = _uiState.value.copy(showAutoDetectDisclosureDialog = true)
                    } else {
                        NotificationAccessHelper.setAutoDetectEnabled(context, true, userKey)
                        _isAutoDetectEnabled.value = true
                    }
                } else {
                    pendingAutoDetectEnable = false
                    NotificationAccessHelper.setAutoDetectEnabled(context, false, userKey)
                    _isAutoDetectEnabled.value = false
                }
            }

            is ProfileUiEvent.OnDismissAutoDetectDisclosure -> {
                pendingAutoDetectEnable = false
                _uiState.value = _uiState.value.copy(showAutoDetectDisclosureDialog = false)
            }

            is ProfileUiEvent.OnOpenNotificationSettings -> {
                _uiState.value = _uiState.value.copy(showAutoDetectDisclosureDialog = false)
                try {
                    context.startActivity(NotificationAccessHelper.getNotificationListenerSettingsIntent())
                } catch (_: Exception) {
                    // Fallback to app details settings if listener settings intent fails
                    try {
                        context.startActivity(NotificationAccessHelper.getAppDetailsSettingsIntent(context))
                    } catch (_: Exception) {}
                }
            }

            is ProfileUiEvent.OnOpenAppSettings -> {
                try {
                    context.startActivity(NotificationAccessHelper.getAppDetailsSettingsIntent(context))
                } catch (_: Exception) {}
            }

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

            is ProfileUiEvent.OnTestAutoDetect -> {
                viewModelScope.launch {
                    val userKey = sessionManager.getCurrentUserKey()
                    val sampleTitle = "HDFC Bank"
                    val sampleBody = "HDFC Bank: Rs 249.00 debited from a/c **1234 on 23-11-26 at SWIGGY. Avl bal: Rs 14,800.00"
                    val parsed = com.waleed.vaulta.android.data.notification.TransactionNotificationParser.parse(
                        packageName = "com.google.android.apps.messaging",
                        title = sampleTitle,
                        text = sampleBody
                    )
                    if (parsed != null) {
                        val expense = com.waleed.vaulta.android.data.model.ExpenseEntity(
                            id = null,
                            title = parsed.title,
                            amount = parsed.amount,
                            date = com.waleed.vaulta.android.utils.Utils.formatDateToHumanReadableForm(parsed.timestamp),
                            type = parsed.type,
                            userId = userKey
                        )
                        val inserted = expenseRepository.insertExpense(expense)
                        if (inserted) {
                            _uiState.value = _uiState.value.copy(
                                message = "Test transaction auto-detected & logged! Check your transaction list."
                            )
                        } else {
                            _uiState.value = _uiState.value.copy(
                                message = "Failed to save test transaction to database."
                            )
                        }
                    } else {
                        _uiState.value = _uiState.value.copy(
                            message = "Test parser failed to detect sample transaction."
                        )
                    }
                }
            }

            is ProfileUiEvent.OnRequestResetEverything -> {
                _uiState.value = _uiState.value.copy(showResetEverythingDialog = true)
            }

            is ProfileUiEvent.OnDismissResetEverything -> {
                _uiState.value = _uiState.value.copy(showResetEverythingDialog = false)
            }

            is ProfileUiEvent.OnConfirmResetEverything -> {
                _uiState.value = _uiState.value.copy(showResetEverythingDialog = false)
                viewModelScope.launch {
                    try {
                        expenseRepository.clearCurrentUserData()
                        budgetRepository.setMonthlyBudget(0.0, emptyList())
                        val userKey = sessionManager.getCurrentUserKey()
                        NotificationAccessHelper.resetAutoDetect(context, userKey)
                        _isAutoDetectEnabled.value = false
                        com.waleed.vaulta.android.widget.VaultaSnackbarController.show(
                            message = "All data has been reset",
                            durationMs = 3500L
                        )
                    } catch (e: Exception) {
                        android.util.Log.e("ProfileViewModel", "Reset everything error", e)
                        com.waleed.vaulta.android.widget.VaultaSnackbarController.show(
                            message = "Reset failed: ${e.message}",
                            durationMs = 3500L
                        )
                    }
                }
            }
        }
    }
}

sealed class ProfileUiEvent : UiEvent() {
    data object OnResume : ProfileUiEvent()
    data class OnToggleAutoDetect(val enabled: Boolean) : ProfileUiEvent()
    data object OnDismissAutoDetectDisclosure : ProfileUiEvent()
    data object OnOpenNotificationSettings : ProfileUiEvent()
    data object OnOpenAppSettings : ProfileUiEvent()
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
    data object OnTestAutoDetect : ProfileUiEvent()
    data object OnRequestResetEverything : ProfileUiEvent()
    data object OnDismissResetEverything : ProfileUiEvent()
    data object OnConfirmResetEverything : ProfileUiEvent()
}
