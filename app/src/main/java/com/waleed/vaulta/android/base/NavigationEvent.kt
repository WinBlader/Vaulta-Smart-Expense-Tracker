package com.waleed.vaulta.android.base

sealed class NavigationEvent {
    object NavigateBack : NavigationEvent()
}

sealed class AddExpenseNavigationEvent : NavigationEvent() {
    object MenuOpenedClicked : AddExpenseNavigationEvent()
}

sealed class HomeNavigationEvent : NavigationEvent() {
    object NavigateToAddExpense : HomeNavigationEvent()
    object NavigateToAddIncome : HomeNavigationEvent()
    object NavigateToSeeAll : HomeNavigationEvent()
    object NavigateToProfile : HomeNavigationEvent()
    object NavigateToSpendsDashboard : HomeNavigationEvent()
}

sealed class AuthNavigationEvent : NavigationEvent() {
    object NavigateToHome : AuthNavigationEvent()
    object NavigateToRegister : AuthNavigationEvent()
    object NavigateToLogin : AuthNavigationEvent()
    object NavigateToPrivacyConsent : AuthNavigationEvent()
}

sealed class ProfileNavigationEvent : NavigationEvent() {
    object NavigateToLogin : ProfileNavigationEvent()
}
