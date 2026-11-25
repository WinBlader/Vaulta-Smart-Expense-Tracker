package com.waleed.vaulta.android.data.repository

import android.util.Log
import com.waleed.vaulta.android.data.CurrencyManager
import com.waleed.vaulta.android.data.auth.supabase.SupabaseClientProvider
import com.waleed.vaulta.android.data.budget.BudgetManager
import com.waleed.vaulta.android.data.dao.ExpenseDao
import com.waleed.vaulta.android.data.model.ExpenseEntity
import com.waleed.vaulta.android.data.model.ExpenseSummary
import com.waleed.vaulta.android.data.model.ProfileDto
import com.waleed.vaulta.android.data.model.TransactionDto
import com.waleed.vaulta.android.data.session.UserSessionManager
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

            if (SupabaseClientProvider.isConfigured() && userKey != "guest" && userKey.isNotBlank()) {
                try {
                    val client = SupabaseClientProvider.client
                    client.from("transactions").delete {
                        filter {
                            eq("user_id", userKey)
                            eq("title", expense.title)
                            eq("amount", expense.amount)
                            eq("date", expense.date)
                        }
                    }
                } catch (e: Exception) {
                    Log.w("ExpenseRepository", "Notice during cloud transaction delete: ${e.message}")
                }
            }

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
     * Restores profile settings (currency, monthly budget, privacy policy) upon fresh install/reinstall.
     */
    override suspend fun syncUserTransactions() = withContext(Dispatchers.IO) {
        val userKey = sessionManager.getCurrentUserKey()
        val currentUser = sessionManager.currentUser.value
        val fallbackEmail = currentUser?.email ?: ""
        if (userKey == "guest" || userKey.isBlank()) return@withContext

        // 1. Run local legacy migration if email exists
        if (fallbackEmail.isNotBlank()) {
            try {
                dao.migrateLegacyUserRows(userKey, fallbackEmail)
            } catch (e: Exception) {
                Log.w("ExpenseRepository", "Notice migrating local legacy rows: ${e.message}")
            }
        }

        // 2. If Supabase is configured and user is not a guest, pull remote profile and transactions
        if (SupabaseClientProvider.isConfigured()) {
            val client = SupabaseClientProvider.client

            // 2a. Reinstall Hydration: Restore Cloud Profile details & Settings
            try {
                val remoteProfile = client.from("profiles").select {
                    filter {
                        or {
                            eq("id", userKey)
                            if (fallbackEmail.isNotBlank()) {
                                eq("email", fallbackEmail)
                            }
                        }
                    }
                }.decodeSingleOrNull<ProfileDto>()

                if (remoteProfile != null) {
                    val restoredName = remoteProfile.displayName?.ifBlank { null }
                        ?: remoteProfile.fullName?.ifBlank { null }
                        ?: remoteProfile.username?.ifBlank { null }
                    if (!restoredName.isNullOrBlank()) {
                        sessionManager.updateUser(
                            name = restoredName,
                            email = remoteProfile.email ?: fallbackEmail,
                            phoneNumber = remoteProfile.phoneNumber ?: ""
                        )
                    }
                    val restoredCurrency = remoteProfile.currencyCode?.ifBlank { null }
                    if (!restoredCurrency.isNullOrBlank()) {
                        CurrencyManager.setCurrencyByCode(restoredCurrency, userKey)
                    }
                    val restoredBudget = remoteProfile.monthlyBudget
                    if (restoredBudget != null && restoredBudget > 0.0) {
                        budgetManager.setMonthlyBudget(restoredBudget)
                    }
                    if (remoteProfile.privacyPolicyAccepted == true) {
                        sessionManager.setPrivacyPolicyAccepted(true, userKey)
                    }
                }
            } catch (e: Exception) {
                Log.w("ExpenseRepository", "Notice restoring cloud profile: ${e.message}")
            }

            // 2b. Reinstall Hydration: Restore Cloud Transactions
            try {
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

                // 2c. Push any unsynced local rows to Cloud
                for (local in localList) {
                    val existsRemotely = remoteTransactions.any { remote ->
                        local.title.equals(remote.title, ignoreCase = true) &&
                        Math.abs(local.amount - remote.amount) < 0.001 &&
                        local.date == remote.date
                    }
                    if (!existsRemotely) {
                        pushTransactionToCloud(local)
                    }
                }

                val finalLocalList = dao.getExpensesListForUser(userKey, fallbackEmail)
                budgetManager.checkBudgetAndNotify(finalLocalList)
                budgetManager.checkBalanceAndNotify(finalLocalList)
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
            val nowStr = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())
            val dto = TransactionDto(
                localId = expense.id,
                userId = expense.userId,
                title = expense.title,
                amount = expense.amount,
                date = expense.date,
                type = expense.type,
                category = expense.type,
                note = expense.note,
                timestamp = System.currentTimeMillis(),
                createdAt = nowStr,
                updatedAt = nowStr
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
            budgetManager.resetBudgetForUser(userKey)

            if (SupabaseClientProvider.isConfigured() && userKey != "guest" && userKey.isNotBlank()) {
                try {
                    val client = SupabaseClientProvider.client
                    client.from("transactions").delete {
                        filter {
                            eq("user_id", userKey)
                        }
                    }
                } catch (e: Exception) {
                    Log.w("ExpenseRepository", "Notice during cloud reset: ${e.message}")
                }
            }

            budgetManager.checkBudgetAndNotify(emptyList())
            budgetManager.checkBalanceAndNotify(emptyList())
        } catch (_: Exception) {}
    }
}
