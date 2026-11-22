package com.waleed.vaulta.android.feature.home

import androidx.lifecycle.viewModelScope
import com.waleed.vaulta.android.base.BaseViewModel
import com.waleed.vaulta.android.base.HomeNavigationEvent
import com.waleed.vaulta.android.base.UiEvent
import com.waleed.vaulta.android.data.CurrencyManager
import com.waleed.vaulta.android.data.model.CurrencyItem
import com.waleed.vaulta.android.data.model.ExpenseEntity
import com.waleed.vaulta.android.data.repository.AuthRepository
import com.waleed.vaulta.android.data.repository.BudgetRepository
import com.waleed.vaulta.android.data.repository.ExpenseRepository
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
    private val budgetRepository: BudgetRepository
) : BaseViewModel() {

    val expenses: Flow<List<ExpenseEntity>> = expenseRepository.getAllExpenses()
    val user = authRepository.currentUser
    val monthlyBudget: StateFlow<Double> = budgetRepository.monthlyBudget

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

    fun getBalance(list: List<ExpenseEntity>, currency: CurrencyItem = CurrencyManager.getSelectedCurrency()): String {
        var balance = 0.0
        for (expense in list) {
            if (expense.type == "Income") {
                balance += expense.amount
            } else {
                balance -= expense.amount
            }
        }
        return Utils.formatCurrency(balance, currency)
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
