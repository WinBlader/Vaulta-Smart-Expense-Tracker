package com.waleed.vaulta.android.feature.budget

import androidx.lifecycle.ViewModel
import com.waleed.vaulta.android.data.model.ExpenseEntity
import com.waleed.vaulta.android.data.repository.BudgetRepository
import com.waleed.vaulta.android.data.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SpendsDashboardViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val budgetRepository: BudgetRepository
) : ViewModel() {

    val expenses: Flow<List<ExpenseEntity>> = expenseRepository.getAllExpenses()
    val monthlyBudget: StateFlow<Double> = budgetRepository.monthlyBudget

    fun calculateCurrentMonthSpends(expenses: List<ExpenseEntity>): Double {
        return budgetRepository.calculateCurrentMonthSpends(expenses)
    }

    fun setMonthlyBudget(amount: Double, expenses: List<ExpenseEntity>) {
        budgetRepository.setMonthlyBudget(amount, expenses)
    }
}
