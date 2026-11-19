package com.waleed.vaulta.android.data.repository

import com.waleed.vaulta.android.data.budget.BudgetManager
import com.waleed.vaulta.android.data.dao.ExpenseDao
import com.waleed.vaulta.android.data.model.ExpenseEntity
import com.waleed.vaulta.android.data.model.ExpenseSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

interface ExpenseRepository {
    fun getAllExpenses(): Flow<List<ExpenseEntity>>
    fun getTopExpenses(): Flow<List<ExpenseEntity>>
    fun getAllExpenseByDate(type: String): Flow<List<ExpenseSummary>>
    suspend fun insertExpense(expense: ExpenseEntity): Boolean
    suspend fun updateExpense(expense: ExpenseEntity): Boolean
    suspend fun deleteExpense(expense: ExpenseEntity): Boolean
}

@Singleton
class ExpenseRepositoryImpl @Inject constructor(
    private val dao: ExpenseDao,
    private val budgetManager: BudgetManager
) : ExpenseRepository {

    override fun getAllExpenses(): Flow<List<ExpenseEntity>> {
        return dao.getAllExpense()
    }

    override fun getTopExpenses(): Flow<List<ExpenseEntity>> {
        return dao.getTopExpenses()
    }

    override fun getAllExpenseByDate(type: String): Flow<List<ExpenseSummary>> {
        return dao.getAllExpenseByDate(type)
    }

    override suspend fun insertExpense(expense: ExpenseEntity): Boolean {
        return try {
            dao.insertExpense(expense)
            val updated = dao.getAllExpense().first()
            budgetManager.checkBudgetAndNotify(updated)
            budgetManager.checkBalanceAndNotify(updated, forceNotifyIfNegative = true)
            true
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun updateExpense(expense: ExpenseEntity): Boolean {
        return try {
            dao.updateExpense(expense)
            val updated = dao.getAllExpense().first()
            budgetManager.checkBudgetAndNotify(updated)
            budgetManager.checkBalanceAndNotify(updated, forceNotifyIfNegative = true)
            true
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun deleteExpense(expense: ExpenseEntity): Boolean {
        return try {
            dao.deleteExpense(expense)
            val updated = dao.getAllExpense().first()
            budgetManager.checkBudgetAndNotify(updated)
            budgetManager.checkBalanceAndNotify(updated, forceNotifyIfNegative = true)
            true
        } catch (_: Exception) {
            false
        }
    }
}
