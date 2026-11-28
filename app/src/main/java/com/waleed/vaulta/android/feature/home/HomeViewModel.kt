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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val authRepository: AuthRepository,
    private val budgetRepository: BudgetRepository,
    private val sessionManager: UserSessionManager,
    private val supabaseAuthService: SupabaseAuthService
) : BaseViewModel() {

    val expenses: Flow<List<ExpenseEntity>> = expenseRepository.getUnifiedTransactions()
    val sharedSplits: Flow<List<com.waleed.vaulta.android.data.model.SharedSplitEntity>> = expenseRepository.getAllSharedSplits()
    val user = authRepository.currentUser
    val monthlyBudget: StateFlow<Double> = budgetRepository.monthlyBudget
    val initialBalance: StateFlow<Double> = sessionManager.initialBalance

    val userBalance: StateFlow<com.waleed.vaulta.android.data.model.UserBalanceEntity?> = sessionManager.currentUser.flatMapLatest { user ->
        val userKey = sessionManager.getCurrentUserKey()
        expenseRepository.observeUserBalance(userKey)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun insertSharedSplits(splits: List<com.waleed.vaulta.android.data.model.SharedSplitEntity>) {
        viewModelScope.launch {
            val success = expenseRepository.insertSharedSplits(splits)
            if (success) {
                com.waleed.vaulta.android.widget.VaultaSnackbarController.show(
                    message = "Split bill created with ${splits.size} members",
                    durationMs = 3000L
                )
            }
        }
    }

    fun saveCreditOwe(
        personName: String,
        amount: Double,
        type: String, // "CREDIT" or "OWE"
        note: String?,
        counterpartyContact: String?,
        counterpartyId: String? = null
    ) {
        viewModelScope.launch {
            val userKey = sessionManager.getCurrentUserKey()
            val entry = com.waleed.vaulta.android.data.model.CreditOweEntity(
                id = java.util.UUID.randomUUID().toString(),
                userId = userKey,
                counterpartyId = counterpartyId,
                personName = personName.trim(),
                counterpartyContact = counterpartyContact?.trim()?.ifBlank { null },
                type = type,
                amount = amount,
                note = note?.trim()?.ifBlank { null },
                status = "PENDING",
                createdAt = System.currentTimeMillis()
            )
            val success = expenseRepository.insertCreditOwe(entry)
            if (success) {
                com.waleed.vaulta.android.widget.VaultaSnackbarController.show(
                    message = "Credit/Owe record saved",
                    durationMs = 3000L
                )
            }
        }
    }

    fun settleCreditOwe(id: String) {
        viewModelScope.launch {
            val success = expenseRepository.settleCreditOwe(id)
            if (success) {
                com.waleed.vaulta.android.widget.VaultaSnackbarController.show(
                    message = "Credit/Owe obligation settled",
                    durationMs = 3000L
                )
            }
        }
    }

    fun deleteCreditOwe(id: String) {
        viewModelScope.launch {
            val success = expenseRepository.deleteCreditOwe(id)
            if (success) {
                com.waleed.vaulta.android.widget.VaultaSnackbarController.show(
                    message = "Credit/Owe record deleted",
                    durationMs = 3000L
                )
            }
        }
    }

    fun settleAllInSplitGroup(groupId: String) {
        viewModelScope.launch {
            val allSplits = expenseRepository.getAllSharedSplits().first()
            val groupSplits = allSplits.filter { (it.groupId == groupId) && it.status.equals("PENDING", ignoreCase = true) }
            for (split in groupSplits) {
                expenseRepository.settleSharedSplit(split.id)
            }
            com.waleed.vaulta.android.widget.VaultaSnackbarController.show(
                message = "All splits in group settled",
                durationMs = 3000L
            )
        }
    }

    suspend fun lookupVaultaContact(query: String): com.waleed.vaulta.android.data.model.ContactLookupResult {
        return expenseRepository.lookupVaultaContact(query)
    }

    suspend fun searchVaultaUsers(query: String): List<com.waleed.vaulta.android.data.model.VaultaUserSearchResult> {
        return expenseRepository.searchVaultaUsers(query)
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

    fun settleSplitCustom(splitsToSettle: List<com.waleed.vaulta.android.data.model.SharedSplitEntity>, customAmounts: Map<String, Double>) {
        viewModelScope.launch {
            for (split in splitsToSettle) {
                val customAmt = customAmounts[split.id]
                if (customAmt != null && customAmt < split.splitAmount && customAmt > 0) {
                    val remaining = split.splitAmount - customAmt
                    // Update split with remaining balance, or mark as settled
                    expenseRepository.updateSharedSplit(split.copy(splitAmount = remaining))
                } else {
                    expenseRepository.settleSharedSplit(split.id)
                }
            }
            com.waleed.vaulta.android.widget.VaultaSnackbarController.show(
                message = "Split settlement applied",
                durationMs = 3000L
            )
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
        val total = getTotalBalanceAmount(list)
        budgetRepository.checkBalanceAndNotify(list, computedTotalBalance = total)
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

    fun deleteItem(item: ExpenseEntity) {
        viewModelScope.launch {
            if (item.uuid.startsWith("co_")) {
                val realId = item.uuid.removePrefix("co_")
                deleteCreditOwe(realId)
            } else if (item.uuid.startsWith("sp_")) {
                val realId = item.uuid.removePrefix("sp_")
                deleteSharedSplit(realId)
            } else {
                deleteExpense(item)
            }
        }
    }

    suspend fun getCreditOweById(id: String): com.waleed.vaulta.android.data.model.CreditOweEntity? {
        return expenseRepository.getCreditOweById(id)
    }

    fun updateCreditOwe(entry: com.waleed.vaulta.android.data.model.CreditOweEntity) {
        viewModelScope.launch {
            expenseRepository.updateCreditOwe(entry)
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
        return userBalance.value?.currentBalance ?: (sessionManager.getInitialBalance() + getRawTransactionNet(list))
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
                expenseRepository.recomputeAndPersistBalance(userKey)
                supabaseAuthService.updateBalancesCloud(newInitialBalance, targetBalance, userKey)
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
