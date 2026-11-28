package com.waleed.vaulta.android.data.repository

import com.waleed.vaulta.android.data.budget.BudgetManager
import com.waleed.vaulta.android.data.model.ExpenseEntity
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

interface BudgetRepository {
    val monthlyBudget: StateFlow<Double>
    fun getMonthlyBudget(): Double
    fun setMonthlyBudget(amount: Double, currentExpenses: List<ExpenseEntity> = emptyList())
    fun calculateCurrentMonthSpends(expenses: List<ExpenseEntity>): Double
    fun checkBudgetAndNotify(expenses: List<ExpenseEntity>)
    fun checkBalanceAndNotify(expenses: List<ExpenseEntity>, computedTotalBalance: Double? = null, forceNotifyIfNegative: Boolean = false)
}

@Singleton
class BudgetRepositoryImpl @Inject constructor(
    private val budgetManager: BudgetManager
) : BudgetRepository {

    override val monthlyBudget: StateFlow<Double> = budgetManager.monthlyBudget

    override fun getMonthlyBudget(): Double = budgetManager.getMonthlyBudget()

    override fun setMonthlyBudget(amount: Double, currentExpenses: List<ExpenseEntity>) {
        budgetManager.setMonthlyBudget(amount, currentExpenses)
    }

    override fun calculateCurrentMonthSpends(expenses: List<ExpenseEntity>): Double {
        return budgetManager.calculateCurrentMonthSpends(expenses)
    }

    override fun checkBudgetAndNotify(expenses: List<ExpenseEntity>) {
        budgetManager.checkBudgetAndNotify(expenses)
    }

    override fun checkBalanceAndNotify(expenses: List<ExpenseEntity>, computedTotalBalance: Double?, forceNotifyIfNegative: Boolean) {
        budgetManager.checkBalanceAndNotify(expenses, computedTotalBalance, forceNotifyIfNegative)
    }
}
