package com.waleed.vaulta.android.data.repository

import android.util.Log
import com.waleed.vaulta.android.data.auth.supabase.SupabaseClientProvider
import com.waleed.vaulta.android.data.budget.BudgetManager
import com.waleed.vaulta.android.data.dao.ExpenseDao
import com.waleed.vaulta.android.data.model.ExpenseEntity
import com.waleed.vaulta.android.data.model.ExpenseSummary
import com.waleed.vaulta.android.data.model.TransactionDto
import com.waleed.vaulta.android.data.session.UserSessionManager
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

interface ExpenseRepository {
    fun getAllExpenses(): Flow<List<ExpenseEntity>>
    fun getTopExpenses(): Flow<List<ExpenseEntity>>
    fun getTopIncomes(): Flow<List<ExpenseEntity>>
    fun getTopCreditOwe(): Flow<List<ExpenseEntity>>
    fun getAllExpenseByDate(type: String): Flow<List<ExpenseSummary>>
    fun getCreditOweExpenseByDate(): Flow<List<ExpenseSummary>>
    suspend fun insertExpense(expense: ExpenseEntity): Boolean
    suspend fun updateExpense(expense: ExpenseEntity): Boolean
    suspend fun deleteExpense(expense: ExpenseEntity): Boolean
    suspend fun syncUserTransactions()
    suspend fun clearCurrentUserData()
}

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ExpenseRepositoryImpl @Inject constructor(
    private val dao: ExpenseDao,
    private val budgetManager: BudgetManager,
    private val sessionManager: UserSessionManager
) : ExpenseRepository {

    override fun getAllExpenses(): Flow<List<ExpenseEntity>> {
        return sessionManager.currentUser.flatMapLatest { user ->
            val userKey = sessionManager.getCurrentUserKey()
            val fallbackEmail = user?.email ?: ""
            dao.getAllExpenseForUser(userKey, fallbackEmail)
        }
    }

    override fun getTopExpenses(): Flow<List<ExpenseEntity>> {
        return sessionManager.currentUser.flatMapLatest { user ->
            val userKey = sessionManager.getCurrentUserKey()
            val fallbackEmail = user?.email ?: ""
            dao.getTopExpensesForUser(userKey, fallbackEmail)
        }
    }

    override fun getTopIncomes(): Flow<List<ExpenseEntity>> {
        return sessionManager.currentUser.flatMapLatest { user ->
            val userKey = sessionManager.getCurrentUserKey()
            val fallbackEmail = user?.email ?: ""
            dao.getTopIncomesForUser(userKey, fallbackEmail)
        }
    }

    override fun getTopCreditOwe(): Flow<List<ExpenseEntity>> {
        return sessionManager.currentUser.flatMapLatest { user ->
            val userKey = sessionManager.getCurrentUserKey()
            val fallbackEmail = user?.email ?: ""
            dao.getTopCreditOweForUser(userKey, fallbackEmail)
        }
    }

    override fun getAllExpenseByDate(type: String): Flow<List<ExpenseSummary>> {
        return sessionManager.currentUser.flatMapLatest { user ->
            val userKey = sessionManager.getCurrentUserKey()
            val fallbackEmail = user?.email ?: ""
            dao.getAllExpenseByDateForUser(type, userKey, fallbackEmail)
        }
    }

    override fun getCreditOweExpenseByDate(): Flow<List<ExpenseSummary>> {
        return sessionManager.currentUser.flatMapLatest { user ->
            val userKey = sessionManager.getCurrentUserKey()
            val fallbackEmail = user?.email ?: ""
            dao.getCreditOweExpenseByDateForUser(userKey, fallbackEmail)
        }
    }

    override suspend fun insertExpense(expense: ExpenseEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            val userKey = sessionManager.getCurrentUserKey()
            val toSave = if (expense.userId.isBlank() || expense.userId == "guest") {
                expense.copy(userId = userKey)
            } else {
                expense
            }
            dao.insertExpense(toSave)
            val updated = dao.getAllExpenseForUser(userKey).first()
            budgetManager.checkBudgetAndNotify(updated)
            budgetManager.checkBalanceAndNotify(updated, forceNotifyIfNegative = true)

            // Asynchronously sync to Supabase if configured
            pushTransactionToCloud(toSave)
            true
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun updateExpense(expense: ExpenseEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            val userKey = sessionManager.getCurrentUserKey()
            val toSave = if (expense.userId.isBlank() || expense.userId == "guest") {
                expense.copy(userId = userKey)
            } else {
                expense
            }
            dao.updateExpense(toSave)
            val updated = dao.getAllExpenseForUser(userKey).first()
            budgetManager.checkBudgetAndNotify(updated)
            budgetManager.checkBalanceAndNotify(updated, forceNotifyIfNegative = true)

            pushTransactionToCloud(toSave)
            true
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun deleteExpense(expense: ExpenseEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            val userKey = sessionManager.getCurrentUserKey()
            dao.deleteExpense(expense)
            val updated = dao.getAllExpenseForUser(userKey).first()
            budgetManager.checkBudgetAndNotify(updated)
            budgetManager.checkBalanceAndNotify(updated, forceNotifyIfNegative = true)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Pulls and merges transactions for the active user from Supabase and pushes any unsynced local rows.
     * Also migrates any legacy rows matching fallbackEmail to canonical userKey.
     */
    override suspend fun syncUserTransactions() = withContext(Dispatchers.IO) {
        val userKey = sessionManager.getCurrentUserKey()
        val currentUser = sessionManager.currentUser.value
        val fallbackEmail = currentUser?.email ?: ""

        // 1. Run local legacy migration if email exists
        if (userKey != "guest" && fallbackEmail.isNotBlank()) {
            try {
                dao.migrateLegacyUserRows(userKey, fallbackEmail)
            } catch (e: Exception) {
                Log.w("ExpenseRepository", "Notice migrating local legacy rows: ${e.message}")
            }
        }

        // 2. If Supabase is configured and user is not a guest, pull remote rows
        if (userKey != "guest" && SupabaseClientProvider.isConfigured()) {
            try {
                val client = SupabaseClientProvider.client
                val remoteTransactions = client.from("transactions").select {
                    filter {
                        or {
                            eq("user_id", userKey)
                            if (fallbackEmail.isNotBlank()) {
                                eq("user_id", fallbackEmail)
                            }
                        }
                    }
                }.decodeList<TransactionDto>()

                if (remoteTransactions.isNotEmpty()) {
                    val localList = dao.getExpensesListForUser(userKey, fallbackEmail)
                    for (remote in remoteTransactions) {
                        // Check if remote already matches a local record by date + title + amount
                        val exists = localList.any { local ->
                            local.title.equals(remote.title, ignoreCase = true) &&
                            Math.abs(local.amount - remote.amount) < 0.001 &&
                            local.date == remote.date
                        }
                        if (!exists) {
                            dao.insertExpense(
                                ExpenseEntity(
                                    id = null,
                                    title = remote.title,
                                    amount = remote.amount,
                                    date = remote.date,
                                    type = remote.type,
                                    userId = userKey,
                                    note = remote.note
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("ExpenseRepository", "Notice during Supabase pull: ${e.message}")
            }
        }
    }

    private suspend fun pushTransactionToCloud(expense: ExpenseEntity) {
        if (!SupabaseClientProvider.isConfigured() || expense.userId == "guest" || expense.userId.isBlank()) {
            return
        }
        try {
            val client = SupabaseClientProvider.client
            val dto = TransactionDto(
                localId = expense.id,
                userId = expense.userId,
                title = expense.title,
                amount = expense.amount,
                date = expense.date,
                type = expense.type,
                note = expense.note
            )
            client.from("transactions").upsert(dto)
        } catch (e: Exception) {
            Log.w("ExpenseRepository", "Notice during Supabase push: ${e.message}")
        }
    }

    override suspend fun clearCurrentUserData() = withContext(Dispatchers.IO) {
        try {
            val userKey = sessionManager.getCurrentUserKey()
            dao.deleteExpensesForUser(userKey)
            budgetManager.checkBudgetAndNotify(emptyList())
            budgetManager.checkBalanceAndNotify(emptyList())
        } catch (_: Exception) {}
    }
}
