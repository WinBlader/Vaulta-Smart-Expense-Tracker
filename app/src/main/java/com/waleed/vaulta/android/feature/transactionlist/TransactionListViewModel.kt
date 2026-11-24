package com.waleed.vaulta.android.feature.transactionlist

import androidx.lifecycle.viewModelScope
import com.waleed.vaulta.android.base.BaseViewModel
import com.waleed.vaulta.android.base.NavigationEvent
import com.waleed.vaulta.android.base.UiEvent
import com.waleed.vaulta.android.data.model.ExpenseEntity
import com.waleed.vaulta.android.data.repository.ExpenseRepository
import com.waleed.vaulta.android.utils.Utils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class FilterParams(
    val query: String = "",
    val type: String = "All",
    val category: String = "All",
    val dateRange: String = "All Time",
    val customStart: Long? = null,
    val customEnd: Long? = null
)

data class TransactionListUiState(
    val transactions: List<ExpenseEntity> = emptyList(),
    val allTransactions: List<ExpenseEntity> = emptyList(),
    val searchQuery: String = "",
    val filterType: String = "All", // "All", "Expense", "Income"
    val selectedCategory: String = "All", // "All" or specific category
    val availableCategories: List<String> = emptyList(),
    val dateRange: String = "All Time", // "All Time", "Today", "This Week", "This Month", "Last Month", "Custom"
    val customStartDateMillis: Long? = null,
    val customEndDateMillis: Long? = null,
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val netBalance: Double = 0.0,
    val isFilterActive: Boolean = false,
    val isLoading: Boolean = false
)

@HiltViewModel
class TransactionListViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository
) : BaseViewModel() {

    private val _filterParams = MutableStateFlow(FilterParams())

    val uiState: StateFlow<TransactionListUiState> = combine(
        expenseRepository.getAllExpenses(),
        _filterParams
    ) { allExpenses, filters ->

        // 1. Search Query Filter
        val queryFiltered = if (filters.query.isBlank()) {
            allExpenses
        } else {
            val q = filters.query.trim().lowercase()
            allExpenses.filter { item ->
                item.title.lowercase().contains(q) ||
                item.type.lowercase().contains(q) ||
                item.amount.toString().contains(q) ||
                item.date.lowercase().contains(q)
            }
        }

        // 2. Type Filter (All, Expense, Income, Credit & Owe)
        val typeFiltered = when (filters.type) {
            "Expense" -> queryFiltered.filter { it.type.equals("Expense", ignoreCase = true) }
            "Income" -> queryFiltered.filter { it.type.equals("Income", ignoreCase = true) }
            "Credit / Owe" -> queryFiltered.filter { it.type.equals("Owed to You", ignoreCase = true) || it.type.equals("You Owe", ignoreCase = true) }
            else -> queryFiltered
        }

        // 3. Category Filter
        val categoryFiltered = if (filters.category == "All") {
            typeFiltered
        } else {
            typeFiltered.filter { it.title.equals(filters.category, ignoreCase = true) }
        }

        // 4. Date Range Filter
        val dateFiltered = filterByDateRange(categoryFiltered, filters.dateRange, filters.customStart, filters.customEnd)

        // Dynamic Calculations
        var incomeSum = 0.0
        var expenseSum = 0.0
        dateFiltered.forEach { item ->
            when (item.type) {
                "Income", "Owed to You", "Credit" -> incomeSum += item.amount
                else -> expenseSum += item.amount
            }
        }
        val net = incomeSum - expenseSum

        val isFilterActive = filters.query.isNotBlank() ||
                filters.type != "All" ||
                filters.category != "All" ||
                filters.dateRange != "All Time"

        // Build list of distinct available categories
        val categoriesSet = linkedSetOf("All")
        allExpenses.forEach { categoriesSet.add(it.title) }
        // Ensure standard presets are available
        listOf("Salary", "Freelance", "Grocery", "Utilities", "Food", "Rent", "Healthcare", "Transport", "Shopping", "Entertainment").forEach {
            categoriesSet.add(it)
        }

        TransactionListUiState(
            transactions = dateFiltered,
            allTransactions = allExpenses,
            searchQuery = filters.query,
            filterType = filters.type,
            selectedCategory = filters.category,
            availableCategories = categoriesSet.toList(),
            dateRange = filters.dateRange,
            customStartDateMillis = filters.customStart,
            customEndDateMillis = filters.customEnd,
            totalIncome = incomeSum,
            totalExpense = expenseSum,
            netBalance = net,
            isFilterActive = isFilterActive,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TransactionListUiState(isLoading = true)
    )

    override fun onEvent(event: UiEvent) {
        when (event) {
            is TransactionListUiEvent.OnSearchQueryChanged -> {
                _filterParams.value = _filterParams.value.copy(query = event.query)
            }
            is TransactionListUiEvent.OnFilterTypeChanged -> {
                _filterParams.value = _filterParams.value.copy(type = event.filterType)
            }
            is TransactionListUiEvent.OnCategoryChanged -> {
                _filterParams.value = _filterParams.value.copy(category = event.category)
            }
            is TransactionListUiEvent.OnDateRangeChanged -> {
                _filterParams.value = _filterParams.value.copy(dateRange = event.dateRange)
            }
            is TransactionListUiEvent.OnCustomDateRangeSelected -> {
                _filterParams.value = _filterParams.value.copy(
                    customStart = event.startMillis,
                    customEnd = event.endMillis,
                    dateRange = "Custom"
                )
            }
            is TransactionListUiEvent.OnResetFilters -> {
                _filterParams.value = FilterParams()
            }
            is TransactionListUiEvent.OnDeleteTransaction -> {
                viewModelScope.launch {
                    val success = expenseRepository.deleteExpense(event.expense)
                    if (success) {
                        com.waleed.vaulta.android.widget.VaultaSnackbarController.show(
                            message = "Transaction deleted",
                            actionLabel = "UNDO",
                            onAction = {
                                viewModelScope.launch {
                                    expenseRepository.insertExpense(event.expense)
                                }
                            },
                            durationMs = 4500L
                        )
                    }
                }
            }
            is TransactionListUiEvent.OnUpdateTransaction -> {
                viewModelScope.launch {
                    val success = expenseRepository.updateExpense(event.expense)
                    if (success) {
                        com.waleed.vaulta.android.widget.VaultaSnackbarController.show(
                            message = "Transaction updated successfully",
                            durationMs = 3000L
                        )
                    }
                }
            }
            is TransactionListUiEvent.OnBackPressed -> {
                viewModelScope.launch {
                    _navigationEvent.emit(NavigationEvent.NavigateBack)
                }
            }
        }
    }

    private fun filterByDateRange(
        list: List<ExpenseEntity>,
        range: String,
        customStart: Long?,
        customEnd: Long?
    ): List<ExpenseEntity> {
        if (range == "All Time") return list

        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val endOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

        val startOfWeek = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val startOfMonth = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val startOfLastMonth = Calendar.getInstance().apply {
            add(Calendar.MONTH, -1)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val endOfLastMonth = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MILLISECOND, -1)
        }.timeInMillis

        return list.filter { item ->
            val itemMillis = Utils.getMillisFromDate(item.date)
            when (range) {
                "Today" -> itemMillis in startOfToday..endOfToday
                "This Week" -> itemMillis in startOfWeek..endOfToday
                "This Month" -> itemMillis in startOfMonth..endOfToday
                "Last Month" -> itemMillis in startOfLastMonth..endOfLastMonth
                "Custom" -> {
                    if (customStart != null && customEnd != null) {
                        val s = Calendar.getInstance().apply {
                            timeInMillis = customStart
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                        val e = Calendar.getInstance().apply {
                            timeInMillis = customEnd
                            set(Calendar.HOUR_OF_DAY, 23)
                            set(Calendar.MINUTE, 59)
                            set(Calendar.SECOND, 59)
                            set(Calendar.MILLISECOND, 999)
                        }.timeInMillis
                        itemMillis in s..e
                    } else true
                }
                else -> true
            }
        }
    }
}

sealed class TransactionListUiEvent : UiEvent() {
    data class OnSearchQueryChanged(val query: String) : TransactionListUiEvent()
    data class OnFilterTypeChanged(val filterType: String) : TransactionListUiEvent()
    data class OnCategoryChanged(val category: String) : TransactionListUiEvent()
    data class OnDateRangeChanged(val dateRange: String) : TransactionListUiEvent()
    data class OnCustomDateRangeSelected(val startMillis: Long, val endMillis: Long) : TransactionListUiEvent()
    data object OnResetFilters : TransactionListUiEvent()
    data class OnDeleteTransaction(val expense: ExpenseEntity) : TransactionListUiEvent()
    data class OnUpdateTransaction(val expense: ExpenseEntity) : TransactionListUiEvent()
    data object OnBackPressed : TransactionListUiEvent()
}
