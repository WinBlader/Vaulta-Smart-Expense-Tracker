package com.waleed.vaulta.android.data.repository

import android.content.Context
import android.util.Log
import com.waleed.vaulta.android.data.CurrencyManager
import com.waleed.vaulta.android.data.auth.supabase.SupabaseClientProvider
import com.waleed.vaulta.android.data.budget.BudgetManager
import com.waleed.vaulta.android.data.dao.ExpenseDao
import com.waleed.vaulta.android.data.model.ExpenseEntity
import com.waleed.vaulta.android.data.model.ExpenseSummary
import com.waleed.vaulta.android.data.model.ProfileDto
import com.waleed.vaulta.android.data.model.RemoteTransactionDto
import com.waleed.vaulta.android.data.session.UserSessionManager
import com.waleed.vaulta.android.data.sync.CloudSyncWorker
import com.waleed.vaulta.android.utils.Utils
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.withContext
import java.util.UUID
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
    suspend fun syncAllCloudData(userId: String)
    suspend fun clearCurrentUserData()
}

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ExpenseRepositoryImpl @Inject constructor(
    private val dao: ExpenseDao,
    private val budgetManager: BudgetManager,
    private val sessionManager: UserSessionManager,
    @ApplicationContext private val context: Context? = null
) : ExpenseRepository {

    private suspend fun ensureAuthenticatedSession(): String? {
        if (!SupabaseClientProvider.isConfigured()) return null
        val client = SupabaseClientProvider.client
        try {
            client.auth.awaitInitialization()
        } catch (e: Exception) {
            Log.e("VaultaCloudSync", "Failed awaiting Supabase auth initialization: ${e.message}", e)
        }

        var currentAuth = client.auth.currentUserOrNull()
        if (currentAuth == null && sessionManager.isLoggedIn()) {
            try {
                client.auth.refreshCurrentSession()
                currentAuth = client.auth.currentUserOrNull()
            } catch (e: Exception) {
                Log.e("VaultaCloudSync", "Failed refreshing Supabase session: ${e.message}", e)
            }
        }
        return currentAuth?.id
    }

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
            val stableUuid = if (expense.uuid.isNotBlank()) expense.uuid else UUID.randomUUID().toString()
            val resolvedUserId = if (expense.userId.isBlank() || expense.userId == "guest") {
                if (userKey.isNotBlank() && userKey != "guest") userKey else expense.userId
            } else {
                expense.userId
            }
            val toSave = expense.copy(
                userId = resolvedUserId,
                uuid = stableUuid
            )
            dao.insertExpense(toSave)
            val updated = dao.getAllExpenseForUser(if (resolvedUserId.isNotBlank()) resolvedUserId else userKey).first()
            budgetManager.checkBudgetAndNotify(updated)
            budgetManager.checkBalanceAndNotify(updated, forceNotifyIfNegative = true)

            // Asynchronously sync to Supabase if authenticated (skip guest)
            pushTransactionToCloud(toSave)
            true
        } catch (e: Exception) {
            Log.e("VaultaCloudSync", "Failed inserting expense locally: ${e.message}", e)
            false
        }
    }

    override suspend fun updateExpense(expense: ExpenseEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            val userKey = sessionManager.getCurrentUserKey()
            val stableUuid = if (expense.uuid.isNotBlank()) expense.uuid else UUID.randomUUID().toString()
            val resolvedUserId = if (expense.userId.isBlank() || expense.userId == "guest") {
                if (userKey.isNotBlank() && userKey != "guest") userKey else expense.userId
            } else {
                expense.userId
            }
            val toSave = expense.copy(
                userId = resolvedUserId,
                uuid = stableUuid
            )
            dao.updateExpense(toSave)
            val updated = dao.getAllExpenseForUser(if (resolvedUserId.isNotBlank()) resolvedUserId else userKey).first()
            budgetManager.checkBudgetAndNotify(updated)
            budgetManager.checkBalanceAndNotify(updated, forceNotifyIfNegative = true)

            pushTransactionToCloud(toSave)
            true
        } catch (e: Exception) {
            Log.e("VaultaCloudSync", "Failed updating expense locally: ${e.message}", e)
            false
        }
    }

    override suspend fun deleteExpense(expense: ExpenseEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            val userKey = sessionManager.getCurrentUserKey()
            dao.deleteExpense(expense)

            if (SupabaseClientProvider.isConfigured() && userKey != "guest" && userKey.isNotBlank()) {
                val authUid = ensureAuthenticatedSession()
                if (!authUid.isNullOrBlank()) {
                    try {
                        val client = SupabaseClientProvider.client
                        val targetUuid = expense.uuid.ifBlank { null }
                        client.from("transactions").delete {
                            filter {
                                eq("user_id", authUid)
                                if (targetUuid != null) {
                                    eq("id", targetUuid)
                                } else {
                                    eq("title", expense.title)
                                    eq("amount", expense.amount)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("VaultaCloudSync", "Notice during cloud transaction delete: ${e.message}", e)
                    }
                }
            }

            val updated = dao.getAllExpenseForUser(userKey).first()
            budgetManager.checkBudgetAndNotify(updated)
            budgetManager.checkBalanceAndNotify(updated, forceNotifyIfNegative = true)
            true
        } catch (e: Exception) {
            Log.e("VaultaCloudSync", "Failed deleting expense: ${e.message}", e)
            false
        }
    }

    override suspend fun syncUserTransactions() = withContext(Dispatchers.IO) {
        val userKey = sessionManager.getCurrentUserKey()
        syncAllCloudData(userKey)
    }

    /**
     * Complete Supabase cloud backup and reinstall restore for ALL user data:
     * - Profiles & preferences (display name, phone number, currency code, monthly budget, privacy policy)
     * - Transactions & Credit/Owe obligations (synced via RemoteTransactionDto)
     * - Offline-first: pushes unsynced local rows and restores missing remote rows into RoomDB
     */
    override suspend fun syncAllCloudData(userId: String) = withContext(Dispatchers.IO) {
        if (userId.isBlank() || userId == "guest") return@withContext
        val currentUser = sessionManager.currentUser.value
        val fallbackEmail = currentUser?.email ?: ""

        val authUid = ensureAuthenticatedSession()
        val effectiveUserId = authUid ?: userId

        // 1. Claim any guest transactions created prior to sign-in
        try {
            dao.claimGuestExpenses(userId)
            if (effectiveUserId != userId) {
                dao.claimGuestExpenses(effectiveUserId)
            }
        } catch (e: Exception) {
            Log.e("VaultaCloudSync", "Notice claiming guest expenses: ${e.message}", e)
        }

        // 2. Local legacy migration if email exists
        if (fallbackEmail.isNotBlank()) {
            try {
                dao.migrateLegacyUserRows(effectiveUserId, fallbackEmail)
                if (effectiveUserId != userId) {
                    dao.migrateLegacyUserRows(userId, fallbackEmail)
                }
            } catch (e: Exception) {
                Log.e("VaultaCloudSync", "Notice migrating legacy rows: ${e.message}", e)
            }
        }

        // 3. Ensure any local rows missing a UUID get assigned a stable UUID
        val initialLocalList = dao.getExpensesListForUser(effectiveUserId, fallbackEmail)
        for (item in initialLocalList) {
            if (item.uuid.isBlank() && item.id != null) {
                val generatedUuid = UUID.randomUUID().toString()
                dao.updateExpenseUuid(item.id, generatedUuid)
            }
        }

        // 4. Supabase Cloud Sync & Restore (Authenticated Users Only)
        if (SupabaseClientProvider.isConfigured() && !authUid.isNullOrBlank()) {
            val client = SupabaseClientProvider.client

            // 4a. Reinstall Hydration: Restore Cloud Profile details & Settings
            try {
                val remoteProfile = client.from("profiles").select {
                    filter {
                        or {
                            eq("id", authUid)
                            if (userId != authUid) {
                                eq("id", userId)
                            }
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

                    // Map "EMPTY" or blank phone numbers to "" locally
                    val cleanPhone = remoteProfile.cleanPhoneNumber

                    if (!restoredName.isNullOrBlank()) {
                        sessionManager.updateUser(
                            name = restoredName,
                            email = remoteProfile.email ?: fallbackEmail,
                            phoneNumber = cleanPhone
                        )
                    } else if (cleanPhone.isNotBlank()) {
                        sessionManager.updateUser(
                            phoneNumber = cleanPhone
                        )
                    }

                    val restoredCurrency = remoteProfile.currencyCode?.ifBlank { null } ?: "INR"
                    CurrencyManager.setCurrencyByCode(restoredCurrency, effectiveUserId)

                    val restoredBudget = remoteProfile.monthlyBudget ?: 0.0
                    if (restoredBudget > 0.0) {
                        budgetManager.setMonthlyBudget(restoredBudget)
                    }

                    val restoredInitialBalance = remoteProfile.initialBalance ?: 0.0
                    if (restoredInitialBalance != 0.0 || sessionManager.getInitialBalance(effectiveUserId) == 0.0) {
                        sessionManager.setInitialBalance(restoredInitialBalance, effectiveUserId)
                    }

                    if (remoteProfile.privacyPolicyAccepted == true) {
                        sessionManager.setPrivacyPolicyAccepted(true, effectiveUserId)
                    }
                }
            } catch (e: Exception) {
                Log.e("VaultaCloudSync", "Notice restoring cloud profile: ${e.message}", e)
            }

            // 4b. Reinstall Hydration: Restore Cloud Transactions
            try {
                val remoteTransactions = client.from("transactions").select {
                    filter {
                        or {
                            eq("user_id", authUid)
                            if (userId != authUid) {
                                eq("user_id", userId)
                            }
                        }
                    }
                }.decodeList<RemoteTransactionDto>()

                val currentLocalList = dao.getExpensesListForUser(effectiveUserId, fallbackEmail)

                for (remote in remoteTransactions) {
                    val localType = if (remote.isCreditOwe) {
                        if (remote.creditOweStatus?.equals("YOU_OWE", ignoreCase = true) == true) {
                            "You Owe"
                        } else {
                            "Owed to You"
                        }
                    } else if (remote.type.equals("INCOME", ignoreCase = true)) {
                        "Income"
                    } else {
                        "Expense"
                    }

                    val formattedDate = Utils.formatDayMonthYear(remote.dateMillis)

                    // Find if matching record already exists locally by uuid
                    val localByUuid = dao.getExpenseByUuid(remote.id)
                    if (localByUuid != null) {
                        // Update in place if attributes changed
                        if (localByUuid.title != remote.title ||
                            Math.abs(localByUuid.amount - remote.amount) >= 0.001 ||
                            localByUuid.type != localType ||
                            localByUuid.note != remote.note) {
                            dao.updateExpense(
                                localByUuid.copy(
                                    title = remote.title,
                                    amount = remote.amount,
                                    type = localType,
                                    date = formattedDate,
                                    note = remote.note,
                                    userId = effectiveUserId
                                )
                            )
                        }
                    } else {
                        // Check if an existing row matches by title + amount + date without uuid
                        val matchWithoutUuid = currentLocalList.find { local ->
                            (local.uuid.isBlank() || local.uuid == remote.id) &&
                            local.title.equals(remote.title, ignoreCase = true) &&
                            Math.abs(local.amount - remote.amount) < 0.001 &&
                            (local.date == formattedDate || Math.abs(Utils.getMillisFromDate(local.date) - remote.dateMillis) < 86400000L)
                        }

                        if (matchWithoutUuid != null) {
                            dao.updateExpense(
                                matchWithoutUuid.copy(
                                    uuid = remote.id,
                                    type = localType,
                                    note = remote.note ?: matchWithoutUuid.note,
                                    userId = effectiveUserId
                                )
                            )
                        } else {
                            dao.insertExpense(
                                ExpenseEntity(
                                    id = null,
                                    title = remote.title,
                                    amount = remote.amount,
                                    date = formattedDate,
                                    type = localType,
                                    userId = effectiveUserId,
                                    note = remote.note,
                                    uuid = remote.id
                                )
                            )
                        }
                    }
                }

                // 4c. Push any unsynced local rows to Cloud
                val latestLocalList = dao.getExpensesListForUser(effectiveUserId, fallbackEmail)
                for (local in latestLocalList) {
                    val matchingRemote = remoteTransactions.find { remote ->
                        remote.id == local.uuid ||
                        (local.title.equals(remote.title, ignoreCase = true) &&
                         Math.abs(local.amount - remote.amount) < 0.001 &&
                         Math.abs(Utils.getMillisFromDate(local.date) - remote.dateMillis) < 86400000L)
                    }
                    val isMissingOrOutdated = matchingRemote == null ||
                        Math.abs(matchingRemote.amount - local.amount) >= 0.001 ||
                        matchingRemote.title != local.title ||
                        matchingRemote.note != local.note

                    if (isMissingOrOutdated) {
                        pushTransactionToCloud(local)
                    }
                }

                val finalLocalList = dao.getExpensesListForUser(effectiveUserId, fallbackEmail)
                budgetManager.checkBudgetAndNotify(finalLocalList)
                budgetManager.checkBalanceAndNotify(finalLocalList)
            } catch (e: Exception) {
                Log.e("VaultaCloudSync", "Failed during Supabase pull/push sync: ${e.message}", e)
                context?.let { CloudSyncWorker.enqueue(it) }
            }
        }
    }

    private suspend fun pushTransactionToCloud(expense: ExpenseEntity) {
        if (!SupabaseClientProvider.isConfigured() || expense.userId == "guest" || expense.userId.isBlank()) {
            return
        }

        val authUid = ensureAuthenticatedSession()
        if (authUid.isNullOrBlank()) {
            Log.w("VaultaCloudSync", "No active Supabase auth session for pushTransactionToCloud (user=${expense.userId}), enqueuing WorkManager retry")
            context?.let { CloudSyncWorker.enqueue(it) }
            return
        }

        try {
            val client = SupabaseClientProvider.client
            val stableUuid = if (expense.uuid.isNotBlank()) expense.uuid else UUID.randomUUID().toString()
            if (expense.uuid.isBlank() && expense.id != null) {
                dao.updateExpenseUuid(expense.id, stableUuid)
            }

            val isCreditOwe = expense.type.equals("Owed to You", ignoreCase = true) ||
                              expense.type.equals("You Owe", ignoreCase = true) ||
                              expense.type.equals("Credit", ignoreCase = true) ||
                              expense.type.equals("Owe", ignoreCase = true)

            val creditOweStatus = if (isCreditOwe) {
                if (expense.type.equals("You Owe", ignoreCase = true) || expense.type.equals("Owe", ignoreCase = true)) {
                    "YOU_OWE"
                } else {
                    "OWED_TO_YOU"
                }
            } else {
                null
            }

            val normalizedType = if (expense.type.equals("Income", ignoreCase = true) ||
                                    expense.type.equals("Owed to You", ignoreCase = true) ||
                                    expense.type.equals("Credit", ignoreCase = true)) {
                "INCOME"
            } else {
                "EXPENSE"
            }

            val dateMillis = Utils.getMillisFromDate(expense.date)

            val remoteDto = RemoteTransactionDto(
                id = stableUuid,
                userId = authUid,
                title = expense.title,
                amount = expense.amount,
                type = normalizedType,
                category = if (isCreditOwe) "Credit / Owe" else expense.title.ifBlank { "General" },
                paymentMethod = "App",
                note = expense.note,
                dateMillis = dateMillis,
                isCreditOwe = isCreditOwe,
                creditOweStatus = creditOweStatus
            )

            client.from("transactions").upsert(remoteDto)
            Log.d("VaultaCloudSync", "Successfully pushed transaction $stableUuid to Supabase for auth user $authUid")
        } catch (e: Exception) {
            Log.e("VaultaCloudSync", "Failed pushing transaction ${expense.uuid} to Supabase: ${e.message}", e)
            context?.let { CloudSyncWorker.enqueue(it) }
        }
    }

    override suspend fun clearCurrentUserData(): Unit = withContext(Dispatchers.IO) {
        try {
            val userKey = sessionManager.getCurrentUserKey()
            dao.deleteExpensesForUser(userKey)
            budgetManager.resetBudgetForUser(userKey)
            sessionManager.setInitialBalance(0.0, userKey)

            if (SupabaseClientProvider.isConfigured() && userKey != "guest" && userKey.isNotBlank()) {
                val authUid = ensureAuthenticatedSession()
                if (!authUid.isNullOrBlank()) {
                    try {
                        val client = SupabaseClientProvider.client
                        // 1. Delete all remote transactions for this user
                        client.from("transactions").delete {
                            filter {
                                eq("user_id", authUid)
                            }
                        }
                        // 2. Reset monthly_budget = 0.0 and initial_balance = 0.0 in public.profiles
                        client.from("profiles").update(mapOf("monthly_budget" to 0.0, "initial_balance" to 0.0)) {
                            filter {
                                eq("id", authUid)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("VaultaCloudSync", "Notice during cloud reset: ${e.message}", e)
                    }
                }
            }

            budgetManager.checkBudgetAndNotify(emptyList())
            budgetManager.checkBalanceAndNotify(emptyList())
        } catch (e: Exception) {
            Log.e("VaultaCloudSync", "Notice during clearCurrentUserData: ${e.message}", e)
        }
        Unit
    }
}
