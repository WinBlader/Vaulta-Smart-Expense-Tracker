package com.waleed.vaulta.android.data.repository

import android.content.Context
import android.util.Log
import com.waleed.vaulta.android.data.CurrencyManager
import com.waleed.vaulta.android.data.auth.supabase.SupabaseClientProvider
import com.waleed.vaulta.android.data.budget.BudgetManager
import com.waleed.vaulta.android.data.dao.ExpenseDao
import com.waleed.vaulta.android.data.dao.SharedSplitDao
import com.waleed.vaulta.android.data.dao.UserBalanceDao
import com.waleed.vaulta.android.data.model.ExpenseEntity
import com.waleed.vaulta.android.data.model.ExpenseSummary
import com.waleed.vaulta.android.data.model.ProfileDto
import com.waleed.vaulta.android.data.model.RemoteTransactionDto
import com.waleed.vaulta.android.data.model.SharedSplitDto
import com.waleed.vaulta.android.data.model.SharedSplitEntity
import com.waleed.vaulta.android.data.model.UserBalanceEntity
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

    // User Balance Persistence
    fun observeUserBalance(userId: String): Flow<UserBalanceEntity?>
    suspend fun getUserBalance(userId: String): UserBalanceEntity?
    suspend fun recomputeAndPersistBalance(userId: String? = null)

    // Shared Splits & Universal Ledger
    fun getAllSharedSplits(): Flow<List<SharedSplitEntity>>
    suspend fun insertSharedSplit(split: SharedSplitEntity): Boolean
    suspend fun insertSharedSplits(splits: List<SharedSplitEntity>): Boolean
    suspend fun updateSharedSplit(split: SharedSplitEntity): Boolean
    suspend fun settleSharedSplit(id: String): Boolean
    suspend fun deleteSharedSplit(id: String): Boolean
    suspend fun syncSharedSplits()
    suspend fun lookupVaultaContact(query: String): com.waleed.vaulta.android.data.model.ContactLookupResult
    suspend fun searchVaultaUsers(query: String): List<com.waleed.vaulta.android.data.model.VaultaUserSearchResult>
    suspend fun setIsSearchable(isSearchable: Boolean): Boolean

    // Dedicated Credit / Owe Ledger
    fun getAllCreditOwe(): Flow<List<com.waleed.vaulta.android.data.model.CreditOweEntity>>
    suspend fun getCreditOweById(id: String): com.waleed.vaulta.android.data.model.CreditOweEntity?
    fun getUnifiedTransactions(): Flow<List<ExpenseEntity>>
    suspend fun insertCreditOwe(entry: com.waleed.vaulta.android.data.model.CreditOweEntity): Boolean
    suspend fun updateCreditOwe(entry: com.waleed.vaulta.android.data.model.CreditOweEntity): Boolean
    suspend fun settleCreditOwe(id: String): Boolean
    suspend fun deleteCreditOwe(id: String): Boolean
    suspend fun syncCreditOwe()
}

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ExpenseRepositoryImpl @Inject constructor(
    private val dao: ExpenseDao,
    private val sharedSplitDao: SharedSplitDao,
    private val userBalanceDao: UserBalanceDao,
    private val creditOweDao: com.waleed.vaulta.android.data.dao.CreditOweDao,
    private val budgetManager: BudgetManager,
    private val sessionManager: UserSessionManager,
    private val supabaseAuthService: com.waleed.vaulta.android.data.auth.supabase.SupabaseAuthService,
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
            val userPhone = user?.phoneNumber ?: ""

            kotlinx.coroutines.flow.combine(
                creditOweDao.getCreditOweForUser(userKey, fallbackEmail),
                sharedSplitDao.getSharedSplitsForUser(userKey, fallbackEmail)
            ) { creditOwes, sharedSplits ->
                val list = mutableListOf<ExpenseEntity>()

                for (co in creditOwes) {
                    if (co.status.equals("PENDING", ignoreCase = true)) {
                        val isCreator = co.userId == userKey
                        val isOwedToMe = if (isCreator) co.type.equals("CREDIT", ignoreCase = true) else !co.type.equals("CREDIT", ignoreCase = true)
                        list.add(
                            ExpenseEntity(
                                id = ("co_${co.id}".hashCode() and 0x7FFFFFFF),
                                title = co.personName,
                                amount = if (co.amount.isNaN()) 0.0 else co.amount.coerceAtLeast(0.0),
                                date = Utils.formatDateToHumanReadableForm(co.createdAt),
                                type = if (isOwedToMe) "Owed to You" else "You Owe",
                                userId = userKey,
                                note = co.note ?: if (isOwedToMe) "Owed to you" else "You owe",
                                uuid = "co_${co.id}"
                            )
                        )
                    }
                }

                for (ss in sharedSplits) {
                    if (ss.status.equals("PENDING", ignoreCase = true)) {
                        val isCreator = ss.creatorId == userKey ||
                            (ss.creatorContact != null && (ss.creatorContact == fallbackEmail || ss.creatorContact == userPhone))
                        val isOwedToMe = if (isCreator) ss.direction == "OWED_TO_CREATOR" else ss.direction != "OWED_TO_CREATOR"
                        val personName = if (isCreator) ss.counterpartyName else ss.creatorName
                        list.add(
                            ExpenseEntity(
                                id = ("sp_${ss.id}".hashCode() and 0x7FFFFFFF),
                                title = personName,
                                amount = if (ss.splitAmount.isNaN()) 0.0 else ss.splitAmount.coerceAtLeast(0.0),
                                date = Utils.formatDateToHumanReadableForm(ss.timestamp),
                                type = if (isOwedToMe) "Owed to You" else "You Owe",
                                userId = userKey,
                                note = ss.title,
                                uuid = "sp_${ss.id}"
                            )
                        )
                    }
                }

                list.sortedByDescending { it.amount }
            }
        }
    }

    override fun getUnifiedTransactions(): Flow<List<ExpenseEntity>> {
        return sessionManager.currentUser.flatMapLatest { user ->
            val userKey = sessionManager.getCurrentUserKey()
            val fallbackEmail = user?.email ?: ""
            val userPhone = user?.phoneNumber ?: ""

            kotlinx.coroutines.flow.combine(
                dao.getAllExpenseForUser(userKey, fallbackEmail),
                creditOweDao.getCreditOweForUser(userKey, fallbackEmail),
                sharedSplitDao.getSharedSplitsForUser(userKey, fallbackEmail)
            ) { expenses, creditOwes, sharedSplits ->
                val combined = mutableListOf<ExpenseEntity>()
                combined.addAll(expenses)

                for (co in creditOwes) {
                    val isCreator = co.userId == userKey
                    val isOwedToMe = if (isCreator) co.type.equals("CREDIT", ignoreCase = true) else !co.type.equals("CREDIT", ignoreCase = true)
                    val isPending = co.status.equals("PENDING", ignoreCase = true)
                    val noteSuffix = if (isPending) "" else " [SETTLED]"
                    combined.add(
                        ExpenseEntity(
                            id = ("co_${co.id}".hashCode() and 0x7FFFFFFF),
                            title = co.personName,
                            amount = if (co.amount.isNaN()) 0.0 else co.amount.coerceAtLeast(0.0),
                            date = Utils.formatDateToHumanReadableForm(co.createdAt),
                            type = if (isOwedToMe) "Owed to You" else "You Owe",
                            userId = userKey,
                            note = (co.note ?: if (isOwedToMe) "Owed to you" else "You owe") + noteSuffix,
                            uuid = "co_${co.id}"
                        )
                    )
                }

                for (ss in sharedSplits) {
                    val isCreator = ss.creatorId == userKey ||
                        (ss.creatorContact != null && (ss.creatorContact == fallbackEmail || ss.creatorContact == userPhone))
                    val isOwedToMe = if (isCreator) ss.direction == "OWED_TO_CREATOR" else ss.direction != "OWED_TO_CREATOR"
                    val personName = if (isCreator) ss.counterpartyName else ss.creatorName
                    val isPending = ss.status.equals("PENDING", ignoreCase = true)
                    val noteSuffix = if (isPending) "" else " [SETTLED]"
                    combined.add(
                        ExpenseEntity(
                            id = ("sp_${ss.id}".hashCode() and 0x7FFFFFFF),
                            title = personName,
                            amount = if (ss.splitAmount.isNaN()) 0.0 else ss.splitAmount.coerceAtLeast(0.0),
                            date = Utils.formatDateToHumanReadableForm(ss.timestamp),
                            type = "Split",
                            userId = userKey,
                            note = ss.title + (if (isOwedToMe) " (owes you)" else " (you owe)") + noteSuffix,
                            uuid = "sp_${ss.id}"
                        )
                    )
                }

                combined.sortedByDescending { Utils.getMillisFromDate(it.date) }
            }
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
            val userPhone = user?.phoneNumber ?: ""

            kotlinx.coroutines.flow.combine(
                creditOweDao.getCreditOweForUser(userKey, fallbackEmail),
                sharedSplitDao.getSharedSplitsForUser(userKey, fallbackEmail)
            ) { creditOwes, sharedSplits ->
                val summaries = mutableListOf<ExpenseSummary>()

                for (co in creditOwes) {
                    if (co.status.equals("PENDING", ignoreCase = true)) {
                        val isCreator = co.userId == userKey
                        val typeStr = if (isCreator) {
                            if (co.type.equals("CREDIT", ignoreCase = true)) "Owed to You" else "You Owe"
                        } else {
                            if (co.type.equals("CREDIT", ignoreCase = true)) "You Owe" else "Owed to You"
                        }
                        summaries.add(
                            ExpenseSummary(
                                type = typeStr,
                                date = Utils.formatDateToHumanReadableForm(co.createdAt),
                                total_amount = co.amount
                            )
                        )
                    }
                }

                for (ss in sharedSplits) {
                    if (ss.status.equals("PENDING", ignoreCase = true)) {
                        val isCreator = ss.creatorId == userKey ||
                            (ss.creatorContact != null && (ss.creatorContact == fallbackEmail || ss.creatorContact == userPhone))
                        val typeStr = if (isCreator) {
                            if (ss.direction == "OWED_TO_CREATOR") "Owed to You" else "You Owe"
                        } else {
                            if (ss.direction == "OWED_TO_CREATOR") "You Owe" else "Owed to You"
                        }
                        summaries.add(
                            ExpenseSummary(
                                type = typeStr,
                                date = Utils.formatDateToHumanReadableForm(ss.timestamp),
                                total_amount = ss.splitAmount
                            )
                        )
                    }
                }

                summaries.groupBy { "${it.date}_${it.type}" }
                    .values
                    .map { group ->
                        val first = group.first()
                        ExpenseSummary(
                            type = first.type,
                            date = first.date,
                            total_amount = group.sumOf { it.total_amount }
                        )
                    }
            }
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
            recomputeAndPersistBalance(resolvedUserId)

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
            recomputeAndPersistBalance(resolvedUserId)

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
            recomputeAndPersistBalance(userKey)
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

                    // Restore or initialize local UserBalanceEntity
                    val restoredCurrentBalance = remoteProfile.currentBalance ?: restoredInitialBalance
                    userBalanceDao.upsert(
                        UserBalanceEntity(
                            userId = effectiveUserId,
                            initialBalance = restoredInitialBalance,
                            currentBalance = restoredCurrentBalance,
                            updatedAt = System.currentTimeMillis()
                        )
                    )

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
                recomputeAndPersistBalance(effectiveUserId)

                // 4d. Reinstall Hydration & Sync: Shared Splits & Universal Ledger
                syncSharedSplitsInternal(effectiveUserId, authUid, fallbackEmail, currentUser?.phoneNumber ?: "")

                // 4e. Reinstall Hydration & Sync: Dedicated Credit / Owe Ledger
                syncCreditOweInternal(effectiveUserId, authUid, fallbackEmail, currentUser?.phoneNumber ?: "")
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
            sharedSplitDao.deleteSharedSplitsForUser(userKey)
            creditOweDao.deleteCreditOweForUser(userKey)
            userBalanceDao.deleteForUser(userKey)
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
                        // 2. Reset monthly_budget = 0.0, initial_balance = 0.0, current_balance = 0.0 in public.profiles
                        client.from("profiles").update(mapOf("monthly_budget" to 0.0, "initial_balance" to 0.0, "current_balance" to 0.0)) {
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
            budgetManager.checkBalanceAndNotify(emptyList(), computedTotalBalance = 0.0)
        } catch (e: Exception) {
            Log.e("VaultaCloudSync", "Notice during clearCurrentUserData: ${e.message}", e)
        }
        Unit
    }

    // ==========================================
    // User Balance Persistence Implementation
    // ==========================================

    override fun observeUserBalance(userId: String): Flow<UserBalanceEntity?> {
        return userBalanceDao.observeBalance(userId)
    }

    override suspend fun getUserBalance(userId: String): UserBalanceEntity? {
        return userBalanceDao.getBalance(userId)
    }

    override suspend fun recomputeAndPersistBalance(userId: String?): Unit = withContext(Dispatchers.IO) {
        try {
            val userKey = userId?.ifBlank { null } ?: sessionManager.getCurrentUserKey()
            val initialBalance = sessionManager.getInitialBalance(userKey)

            val expenses = dao.getExpensesListForUser(userKey, "")
            var netTransactions = 0.0
            for (expense in expenses) {
                if (expense.type.equals("Income", ignoreCase = true)) {
                    netTransactions += expense.amount
                } else {
                    netTransactions -= expense.amount
                }
            }

            // 1. Credit / Owe cash balance impact
            val creditOweList = creditOweDao.getCreditOweCreatedByUser(userKey)
            var creditOweNet = 0.0
            for (item in creditOweList) {
                if (item.status.equals("PENDING", ignoreCase = true)) {
                    if (item.type.equals("CREDIT", ignoreCase = true)) {
                        creditOweNet -= item.amount // lent out: cash leaves
                    } else {
                        creditOweNet += item.amount // borrowed: cash arrives
                    }
                }
            }

            // 2. Split Bill cash balance impact
            val userSplits = sharedSplitDao.getSplitsCreatedByUser(userKey)
            val splitGroups = userSplits.groupBy { it.groupId ?: "${it.title}_${it.timestamp}" }
            var splitBillNet = 0.0
            for ((_, splitsInGroup) in splitGroups) {
                val totalBillPaid = splitsInGroup.firstOrNull()?.totalAmount ?: 0.0
                val settledReimbursed = splitsInGroup.filter { it.status.equals("SETTLED", ignoreCase = true) }.sumOf { it.splitAmount }
                splitBillNet += (-totalBillPaid + settledReimbursed)
            }

            val liveCurrentBalance = initialBalance + netTransactions + creditOweNet + splitBillNet
            val balanceEntity = UserBalanceEntity(
                userId = userKey,
                initialBalance = initialBalance,
                currentBalance = liveCurrentBalance,
                updatedAt = System.currentTimeMillis()
            )
            userBalanceDao.upsert(balanceEntity)

            // Trigger negative balance check with true totalBalance
            budgetManager.checkBalanceAndNotify(expenses, computedTotalBalance = liveCurrentBalance)

            // If userKey is authenticated, push updated balances to Supabase public.profiles
            if (SupabaseClientProvider.isConfigured() && userKey != "guest" && userKey.isNotBlank()) {
                val authUid = ensureAuthenticatedSession()
                if (!authUid.isNullOrBlank()) {
                    try {
                        SupabaseClientProvider.client.from("profiles").update(
                            mapOf(
                                "initial_balance" to initialBalance,
                                "current_balance" to liveCurrentBalance
                            )
                        ) {
                            filter {
                                eq("id", authUid)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("VaultaCloudSync", "Notice updating balance in cloud: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("VaultaCloudSync", "Error recomputing user balance: ${e.message}", e)
        }
    }

    // ==========================================
    // Shared Splits & Universal Ledger Implementation
    // ==========================================

    override fun getAllSharedSplits(): Flow<List<SharedSplitEntity>> {
        return sessionManager.currentUser.flatMapLatest { user ->
            val userKey = sessionManager.getCurrentUserKey()
            val contact = user?.email?.ifBlank { user.phoneNumber } ?: ""
            sharedSplitDao.getSharedSplitsForUser(userKey, contact)
        }
    }

    override suspend fun insertSharedSplit(split: SharedSplitEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            sharedSplitDao.insertSharedSplit(split)
            pushSharedSplitToCloud(split)
            recomputeAndPersistBalance(split.creatorId)
            true
        } catch (e: Exception) {
            Log.e("VaultaCloudSync", "Error inserting shared split: ${e.message}", e)
            false
        }
    }

    override suspend fun insertSharedSplits(splits: List<SharedSplitEntity>): Boolean = withContext(Dispatchers.IO) {
        try {
            sharedSplitDao.insertSharedSplits(splits)
            for (split in splits) {
                pushSharedSplitToCloud(split)
            }
            val creatorKey = splits.firstOrNull()?.creatorId
            recomputeAndPersistBalance(creatorKey)
            true
        } catch (e: Exception) {
            Log.e("VaultaCloudSync", "Error inserting shared splits: ${e.message}", e)
            false
        }
    }

    override suspend fun updateSharedSplit(split: SharedSplitEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            sharedSplitDao.updateSharedSplit(split)
            pushSharedSplitToCloud(split)
            recomputeAndPersistBalance(split.creatorId)
            true
        } catch (e: Exception) {
            Log.e("VaultaCloudSync", "Error updating shared split: ${e.message}", e)
            false
        }
    }

    override suspend fun settleSharedSplit(id: String): Boolean = withContext(Dispatchers.IO) {
        try {
            sharedSplitDao.updateStatus(id, "SETTLED")
            val split = sharedSplitDao.getSharedSplitById(id)
            if (split != null) {
                pushSharedSplitToCloud(split)
                recomputeAndPersistBalance(split.creatorId)
            } else {
                recomputeAndPersistBalance()
            }
            true
        } catch (e: Exception) {
            Log.e("VaultaCloudSync", "Error settling shared split: ${e.message}", e)
            false
        }
    }

    override suspend fun deleteSharedSplit(id: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val existingSplit = sharedSplitDao.getSharedSplitById(id)
            sharedSplitDao.deleteSharedSplitById(id)
            if (SupabaseClientProvider.isConfigured()) {
                val authUid = ensureAuthenticatedSession()
                if (!authUid.isNullOrBlank()) {
                    try {
                        SupabaseClientProvider.client.from("shared_splits").delete {
                            filter {
                                eq("id", id)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("VaultaCloudSync", "Notice deleting remote shared split: ${e.message}", e)
                    }
                }
            }
            recomputeAndPersistBalance(existingSplit?.creatorId)
            true
        } catch (e: Exception) {
            Log.e("VaultaCloudSync", "Error deleting shared split: ${e.message}", e)
            false
        }
    }

    override suspend fun syncSharedSplits(): Unit = withContext(Dispatchers.IO) {
        val user = sessionManager.currentUser.first()
        val userKey = sessionManager.getCurrentUserKey()
        val authUid = ensureAuthenticatedSession()
        if (!authUid.isNullOrBlank()) {
            syncSharedSplitsInternal(userKey, authUid, user?.email ?: "", user?.phoneNumber ?: "")
        }
    }

    private suspend fun syncSharedSplitsInternal(
        userKey: String,
        authUid: String,
        email: String,
        phone: String
    ) {
        if (!SupabaseClientProvider.isConfigured() || authUid.isBlank()) return
        try {
            val client = SupabaseClientProvider.client

            // 1. Fetch remote splits involving this user
            val remoteSplits = client.from("shared_splits").select {
                filter {
                    or {
                        eq("creator_id", authUid)
                        if (userKey != authUid) {
                            eq("creator_id", userKey)
                        }
                        eq("counterparty_id", authUid)
                        if (email.isNotBlank()) {
                            eq("counterparty_contact", email)
                        }
                        if (phone.isNotBlank()) {
                            eq("counterparty_contact", phone)
                        }
                    }
                }
            }.decodeList<SharedSplitDto>()

            for (remote in remoteSplits) {
                // If counterparty_contact matched our email or phone, but counterparty_id wasn't set, claim it
                val shouldClaimCounterparty = remote.counterpartyId.isNullOrBlank() &&
                    ((email.isNotBlank() && remote.counterpartyContact == email) ||
                     (phone.isNotBlank() && remote.counterpartyContact == phone))

                val effectiveCounterpartyId = if (shouldClaimCounterparty) authUid else remote.counterpartyId

                val entity = SharedSplitEntity(
                    id = remote.id,
                    groupId = remote.groupId,
                    title = remote.title,
                    totalAmount = remote.totalAmount,
                    splitAmount = remote.splitAmount,
                    creatorId = remote.creatorId,
                    creatorName = remote.creatorName,
                    creatorContact = remote.creatorContact,
                    counterpartyId = effectiveCounterpartyId,
                    counterpartyName = remote.counterpartyName,
                    counterpartyContact = remote.counterpartyContact,
                    direction = remote.direction,
                    status = remote.status,
                    timestamp = remote.timestamp,
                    notes = remote.notes
                )
                sharedSplitDao.insertSharedSplit(entity)

                if (shouldClaimCounterparty) {
                    try {
                        client.from("shared_splits").update(mapOf("counterparty_id" to authUid)) {
                            filter {
                                eq("id", remote.id)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("VaultaCloudSync", "Notice updating counterparty_id: ${e.message}", e)
                    }
                }
            }

            // 2. Push any local splits created by this user that are not on cloud or need sync
            val localCreated = sharedSplitDao.getSplitsCreatedByUser(userKey)
            for (local in localCreated) {
                val existsRemote = remoteSplits.any { it.id == local.id }
                if (!existsRemote) {
                    pushSharedSplitToCloud(local)
                }
            }
        } catch (e: Exception) {
            Log.e("VaultaCloudSync", "Notice syncing shared splits: ${e.message}", e)
        }
    }

    override suspend fun lookupVaultaContact(query: String): com.waleed.vaulta.android.data.model.ContactLookupResult {
        return supabaseAuthService.lookupVaultaContact(query)
    }

    override suspend fun searchVaultaUsers(query: String): List<com.waleed.vaulta.android.data.model.VaultaUserSearchResult> {
        return supabaseAuthService.searchVaultaUsers(query)
    }

    override suspend fun setIsSearchable(isSearchable: Boolean): Boolean {
        return supabaseAuthService.setIsSearchable(isSearchable)
    }

    private suspend fun pushSharedSplitToCloud(split: SharedSplitEntity) {
        if (!SupabaseClientProvider.isConfigured()) return
        val authUid = ensureAuthenticatedSession() ?: return
        try {
            val client = SupabaseClientProvider.client
            val dto = SharedSplitDto(
                id = split.id,
                groupId = split.groupId,
                title = split.title,
                totalAmount = split.totalAmount,
                splitAmount = split.splitAmount,
                creatorId = if (split.creatorId == "guest" || split.creatorId.isBlank()) authUid else split.creatorId,
                creatorName = split.creatorName,
                creatorContact = split.creatorContact,
                counterpartyId = split.counterpartyId,
                counterpartyName = split.counterpartyName,
                counterpartyContact = split.counterpartyContact,
                direction = split.direction,
                status = split.status,
                timestamp = split.timestamp,
                notes = split.notes
            )
            client.from("shared_splits").upsert(dto)
            Log.d("VaultaCloudSync", "Successfully pushed shared split ${split.id} to Supabase")
        } catch (e: Exception) {
            Log.e("VaultaCloudSync", "Notice pushing shared split ${split.id}: ${e.message}", e)
        }
    }

    // ==========================================
    // Dedicated Credit / Owe Ledger Implementation
    // ==========================================

    override fun getAllCreditOwe(): Flow<List<com.waleed.vaulta.android.data.model.CreditOweEntity>> {
        return sessionManager.currentUser.flatMapLatest { user ->
            val userKey = sessionManager.getCurrentUserKey()
            val contact = user?.email?.ifBlank { user.phoneNumber } ?: ""
            creditOweDao.getCreditOweForUser(userKey, contact)
        }
    }

    override suspend fun getCreditOweById(id: String): com.waleed.vaulta.android.data.model.CreditOweEntity? = withContext(Dispatchers.IO) {
        creditOweDao.getCreditOweById(id)
    }

    override suspend fun insertCreditOwe(entry: com.waleed.vaulta.android.data.model.CreditOweEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            creditOweDao.insertCreditOwe(entry)
            pushCreditOweToCloud(entry)
            recomputeAndPersistBalance(entry.userId)
            true
        } catch (e: Exception) {
            Log.e("VaultaCloudSync", "Error inserting credit_owe: ${e.message}", e)
            false
        }
    }

    override suspend fun updateCreditOwe(entry: com.waleed.vaulta.android.data.model.CreditOweEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            creditOweDao.updateCreditOwe(entry)
            pushCreditOweToCloud(entry)
            recomputeAndPersistBalance(entry.userId)
            true
        } catch (e: Exception) {
            Log.e("VaultaCloudSync", "Error updating credit_owe: ${e.message}", e)
            false
        }
    }

    override suspend fun settleCreditOwe(id: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            creditOweDao.updateStatus(id, "SETTLED", now)
            if (SupabaseClientProvider.isConfigured()) {
                val client = SupabaseClientProvider.client
                client.from("credit_owe").update(mapOf("status" to "SETTLED", "settled_at" to now)) {
                    filter {
                        eq("id", id)
                    }
                }
            }
            recomputeAndPersistBalance()
            true
        } catch (e: Exception) {
            Log.e("VaultaCloudSync", "Error settling credit_owe: ${e.message}", e)
            false
        }
    }

    override suspend fun deleteCreditOwe(id: String): Boolean = withContext(Dispatchers.IO) {
        try {
            creditOweDao.deleteCreditOweById(id)
            if (SupabaseClientProvider.isConfigured()) {
                val client = SupabaseClientProvider.client
                client.from("credit_owe").delete {
                    filter {
                        eq("id", id)
                    }
                }
            }
            recomputeAndPersistBalance()
            true
        } catch (e: Exception) {
            Log.e("VaultaCloudSync", "Error deleting credit_owe: ${e.message}", e)
            false
        }
    }

    override suspend fun syncCreditOwe(): Unit = withContext(Dispatchers.IO) {
        val user = sessionManager.currentUser.first()
        val userKey = sessionManager.getCurrentUserKey()
        val authUid = ensureAuthenticatedSession()
        if (!authUid.isNullOrBlank()) {
            syncCreditOweInternal(userKey, authUid, user?.email ?: "", user?.phoneNumber ?: "")
        }
    }

    private suspend fun syncCreditOweInternal(
        userKey: String,
        authUid: String,
        email: String,
        phone: String
    ) {
        if (!SupabaseClientProvider.isConfigured() || authUid.isBlank()) return
        try {
            val client = SupabaseClientProvider.client
            val remoteEntries = client.from("credit_owe").select {
                filter {
                    or {
                        eq("user_id", authUid)
                        if (userKey != authUid) {
                            eq("user_id", userKey)
                        }
                        eq("counterparty_id", authUid)
                        if (email.isNotBlank()) {
                            eq("counterparty_contact", email)
                        }
                        if (phone.isNotBlank()) {
                            eq("counterparty_contact", phone)
                        }
                    }
                }
            }.decodeList<com.waleed.vaulta.android.data.model.CreditOweDto>()

            for (remote in remoteEntries) {
                val shouldClaimCounterparty = remote.counterpartyId.isNullOrBlank() &&
                    ((email.isNotBlank() && remote.counterpartyContact == email) ||
                     (phone.isNotBlank() && remote.counterpartyContact == phone))

                val effectiveCounterpartyId = if (shouldClaimCounterparty) authUid else remote.counterpartyId

                val entity = com.waleed.vaulta.android.data.model.CreditOweEntity(
                    id = remote.id,
                    userId = remote.userId,
                    counterpartyId = effectiveCounterpartyId,
                    personName = remote.personName,
                    counterpartyContact = remote.counterpartyContact,
                    type = remote.type,
                    amount = remote.amount,
                    note = remote.note,
                    status = remote.status,
                    createdAt = remote.createdAt,
                    settledAt = remote.settledAt
                )
                creditOweDao.insertCreditOwe(entity)

                if (shouldClaimCounterparty) {
                    try {
                        client.from("credit_owe").update(mapOf("counterparty_id" to authUid)) {
                            filter {
                                eq("id", remote.id)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("VaultaCloudSync", "Notice updating credit_owe counterparty_id: ${e.message}", e)
                    }
                }
            }

            val localCreated = creditOweDao.getCreditOweCreatedByUser(userKey)
            for (local in localCreated) {
                val existsRemote = remoteEntries.any { it.id == local.id }
                if (!existsRemote) {
                    pushCreditOweToCloud(local)
                }
            }
            recomputeAndPersistBalance(userKey)
        } catch (e: Exception) {
            Log.e("VaultaCloudSync", "Notice syncing credit_owe: ${e.message}", e)
        }
    }

    private suspend fun pushCreditOweToCloud(entry: com.waleed.vaulta.android.data.model.CreditOweEntity) {
        if (!SupabaseClientProvider.isConfigured()) return
        val authUid = ensureAuthenticatedSession() ?: return
        try {
            val client = SupabaseClientProvider.client
            val dto = com.waleed.vaulta.android.data.model.CreditOweDto(
                id = entry.id,
                userId = if (entry.userId == "guest" || entry.userId.isBlank()) authUid else entry.userId,
                counterpartyId = entry.counterpartyId,
                personName = entry.personName,
                counterpartyContact = entry.counterpartyContact,
                type = entry.type,
                amount = entry.amount,
                note = entry.note,
                status = entry.status,
                createdAt = entry.createdAt,
                settledAt = entry.settledAt
            )
            client.from("credit_owe").upsert(dto)
            Log.d("VaultaCloudSync", "Successfully pushed credit_owe ${entry.id} to Supabase")
        } catch (e: Exception) {
            Log.e("VaultaCloudSync", "Notice pushing credit_owe ${entry.id}: ${e.message}", e)
        }
    }
}
