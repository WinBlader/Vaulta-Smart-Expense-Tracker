package com.waleed.vaulta.android.feature.home

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.waleed.vaulta.android.base.BaseViewModel
import com.waleed.vaulta.android.base.HomeNavigationEvent
import com.waleed.vaulta.android.base.UiEvent
import com.waleed.vaulta.android.data.CurrencyManager
import com.waleed.vaulta.android.data.auth.supabase.SupabaseAuthService
import com.waleed.vaulta.android.data.model.CurrencyItem
import com.waleed.vaulta.android.data.model.ExpenseEntity
import com.waleed.vaulta.android.data.repository.AuthRepository
import com.waleed.vaulta.android.data.repository.BudgetRepository
import com.waleed.vaulta.android.data.repository.ExpenseRepository
import com.waleed.vaulta.android.data.session.UserSessionManager
import com.waleed.vaulta.android.utils.Utils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val authRepository: AuthRepository,
    private val budgetRepository: BudgetRepository,
    private val sessionManager: UserSessionManager,
    private val supabaseAuthService: SupabaseAuthService
) : BaseViewModel() {

    val expenses: Flow<List<ExpenseEntity>> = expenseRepository.getAllExpenses()
    val sharedSplits: Flow<List<com.waleed.vaulta.android.data.model.SharedSplitEntity>> = expenseRepository.getAllSharedSplits()
    val user = authRepository.currentUser
    val monthlyBudget: StateFlow<Double> = budgetRepository.monthlyBudget
    val initialBalance: StateFlow<Double> = sessionManager.initialBalance

    fun insertSharedSplits(splits: List<com.waleed.vaulta.android.data.model.SharedSplitEntity>, creatorExpense: ExpenseEntity?) {
        viewModelScope.launch {
            if (creatorExpense != null) {
                expenseRepository.insertExpense(creatorExpense)
            }
            val success = expenseRepository.insertSharedSplits(splits)
            if (success) {
                com.waleed.vaulta.android.widget.VaultaSnackbarController.show(
                    message = "Split bill created with ${splits.size} members",
                    durationMs = 3000L
                )
            }
        }
    }

    fun saveCreditOwe(expense: ExpenseEntity, counterpartyContact: String?) {
        viewModelScope.launch {
            val success = expenseRepository.insertExpense(expense)
            if (success) {
                if (!counterpartyContact.isNullOrBlank()) {
                    val userKey = sessionManager.getCurrentUserKey()
                    val currentUser = user.value
                    val isOwedToUser = expense.type.equals("Owed to You", ignoreCase = true)
                    val splitEntity = com.waleed.vaulta.android.data.model.SharedSplitEntity(
                        id = if (expense.uuid.isNotBlank()) expense.uuid else java.util.UUID.randomUUID().toString(),
                        title = expense.title,
                        totalAmount = expense.amount,
                        splitAmount = expense.amount,
                        creatorId = userKey,
                        creatorName = currentUser?.name?.ifBlank { "Vaulta User" } ?: "Vaulta User",
                        creatorContact = currentUser?.email?.ifBlank { currentUser.phoneNumber },
                        counterpartyId = null,
                        counterpartyName = expense.title,
                        counterpartyContact = counterpartyContact,
                        direction = if (isOwedToUser) "OWED_TO_CREATOR" else "CREATOR_OWES",
                        status = "PENDING",
                        timestamp = Utils.getMillisFromDate(expense.date),
                        notes = expense.note
                    )
                    expenseRepository.insertSharedSplit(splitEntity)
                }
                com.waleed.vaulta.android.widget.VaultaSnackbarController.show(
                    message = "Credit/Owe record saved",
                    durationMs = 3000L
                )
            }
        }
    }

    fun settleSharedSplit(id: String) {
        viewModelScope.launch {
            val success = expenseRepository.settleSharedSplit(id)
            if (success) {
                com.waleed.vaulta.android.widget.VaultaSnackbarController.show(
                    message = "Split settled successfully",
                    durationMs = 3000L
                )
            }
        }
    }

    fun deleteSharedSplit(id: String) {
        viewModelScope.launch {
            val success = expenseRepository.deleteSharedSplit(id)
            if (success) {
                com.waleed.vaulta.android.widget.VaultaSnackbarController.show(
                    message = "Split entry removed",
                    durationMs = 3000L
                )
            }
        }
    }

    fun getCurrentMonthSpends(list: List<ExpenseEntity>): Double {
        return budgetRepository.calculateCurrentMonthSpends(list)
    }

    fun setMonthlyBudget(amount: Double, list: List<ExpenseEntity>) {
        budgetRepository.setMonthlyBudget(amount, list)
    }

    fun checkBudgetAndNotify(list: List<ExpenseEntity>) {
        budgetRepository.checkBudgetAndNotify(list)
    }

    fun checkBalanceAndNotify(list: List<ExpenseEntity>) {
        budgetRepository.checkBalanceAndNotify(list)
    }

    fun insertExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            val success = expenseRepository.insertExpense(expense)
            if (success) {
                com.waleed.vaulta.android.widget.VaultaSnackbarController.show(
                    message = "Transaction added successfully",
                    durationMs = 3000L
                )
            }
        }
    }

    fun updateExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            val success = expenseRepository.updateExpense(expense)
            if (success) {
                com.waleed.vaulta.android.widget.VaultaSnackbarController.show(
                    message = "Transaction updated successfully",
                    durationMs = 3000L
                )
            }
        }
    }

    fun deleteExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            val success = expenseRepository.deleteExpense(expense)
            if (success) {
                com.waleed.vaulta.android.widget.VaultaSnackbarController.show(
                    message = "Transaction deleted",
                    actionLabel = "UNDO",
                    onAction = {
                        viewModelScope.launch {
                            expenseRepository.insertExpense(expense)
                        }
                    },
                    durationMs = 4500L
                )
            }
        }
    }

    override fun onEvent(event: UiEvent) {
        when (event) {
            is HomeUiEvent.OnAddExpenseClicked -> {
                viewModelScope.launch {
                    _navigationEvent.emit(HomeNavigationEvent.NavigateToAddExpense)
                }
            }

            is HomeUiEvent.OnAddIncomeClicked -> {
                viewModelScope.launch {
                    _navigationEvent.emit(HomeNavigationEvent.NavigateToAddIncome)
                }
            }

            is HomeUiEvent.OnSeeAllClicked -> {
                viewModelScope.launch {
                    _navigationEvent.emit(HomeNavigationEvent.NavigateToSeeAll)
                }
            }

            is HomeUiEvent.OnProfileClicked -> {
                viewModelScope.launch {
                    _navigationEvent.emit(HomeNavigationEvent.NavigateToProfile)
                }
            }

            is HomeUiEvent.OnSpendsDashboardClicked -> {
                viewModelScope.launch {
                    _navigationEvent.emit(HomeNavigationEvent.NavigateToSpendsDashboard)
                }
            }
        }
    }

    fun getRawTransactionNet(list: List<ExpenseEntity>): Double {
        var balance = 0.0
        for (expense in list) {
            when (expense.type) {
                "Income", "Owed to You", "Credit" -> balance += expense.amount
                else -> balance -= expense.amount
            }
        }
        return balance
    }

    fun getTotalBalanceAmount(list: List<ExpenseEntity>): Double {
        return sessionManager.getInitialBalance() + getRawTransactionNet(list)
    }

    fun getBalance(list: List<ExpenseEntity>, currency: CurrencyItem = CurrencyManager.getSelectedCurrency()): String {
        val total = getTotalBalanceAmount(list)
        return Utils.formatCurrency(total, currency)
    }

    fun setTargetCurrentBalance(targetBalance: Double, list: List<ExpenseEntity>) {
        val netTransactions = getRawTransactionNet(list)
        val newInitialBalance = targetBalance - netTransactions
        val userKey = sessionManager.getCurrentUserKey()
        sessionManager.setInitialBalance(newInitialBalance, userKey)
        viewModelScope.launch {
            try {
                supabaseAuthService.updateInitialBalanceCloud(newInitialBalance, userKey)
            } catch (e: Exception) {
                Log.w("HomeViewModel", "Notice syncing initial balance to cloud: ${e.message}")
            }
        }
    }

    fun getTotalExpense(list: List<ExpenseEntity>, currency: CurrencyItem = CurrencyManager.getSelectedCurrency()): String {
        var total = 0.0
        for (expense in list) {
            if (expense.type != "Income") {
                total += expense.amount
            }
        }
        return Utils.formatCurrency(total)
    }

    fun getTotalIncome(list: List<ExpenseEntity>, currency: CurrencyItem = CurrencyManager.getSelectedCurrency()): String {
        var totalIncome = 0.0
        for (expense in list) {
            if (expense.type == "Income") {
                totalIncome += expense.amount
            }
        }
        return Utils.formatCurrency(totalIncome, currency)
    }
}

sealed class HomeUiEvent : UiEvent() {
    data object OnAddExpenseClicked : HomeUiEvent()
    data object OnAddIncomeClicked : HomeUiEvent()
    data object OnSeeAllClicked : HomeUiEvent()
    data object OnProfileClicked : HomeUiEvent()
    data object OnSpendsDashboardClicked : HomeUiEvent()
}
