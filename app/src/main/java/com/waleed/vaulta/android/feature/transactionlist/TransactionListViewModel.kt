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

data class TransactionListUiState(
    val transactions: List<ExpenseEntity> = emptyList(),
    val filterType: String = "All",
    val dateRange: String = "All Time",
    val isLoading: Boolean = false
)

@HiltViewModel
class TransactionListViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository
) : BaseViewModel() {

    private val _filterType = MutableStateFlow("All")
    private val _dateRange = MutableStateFlow("All Time")

    val uiState: StateFlow<TransactionListUiState> = combine(
        expenseRepository.getAllExpenses(),
        _filterType,
        _dateRange
    ) { allExpenses, filter, dateRange ->
        val filteredByType = when (filter) {
            "Expense" -> allExpenses.filter { it.type == "Expense" }
            "Income" -> allExpenses.filter { it.type == "Income" }
            else -> allExpenses
        }

        val filteredByDate = filterByDateRange(filteredByType, dateRange)

        TransactionListUiState(
            transactions = filteredByDate,
            filterType = filter,
            dateRange = dateRange,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TransactionListUiState(isLoading = true)
    )

    override fun onEvent(event: UiEvent) {
        when (event) {
            is TransactionListUiEvent.OnFilterTypeChanged -> {
                _filterType.value = event.filterType
            }
            is TransactionListUiEvent.OnDateRangeChanged -> {
                _dateRange.value = event.dateRange
            }
            is TransactionListUiEvent.OnBackPressed -> {
                viewModelScope.launch {
                    _navigationEvent.emit(NavigationEvent.NavigateBack)
                }
            }
        }
    }

    private fun filterByDateRange(list: List<ExpenseEntity>, range: String): List<ExpenseEntity> {
        if (range == "All Time") return list

        val now = Calendar.getInstance()
        return list.filter { item ->
            val itemMillis = Utils.getMillisFromDate(item.date)
            val itemCal = Calendar.getInstance().apply { timeInMillis = itemMillis }

            when (range) {
                "This Month" -> {
                    itemCal.get(Calendar.MONTH) == now.get(Calendar.MONTH) &&
                            itemCal.get(Calendar.YEAR) == now.get(Calendar.YEAR)
                }
                "Last 7 Days" -> {
                    val sevenDaysAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)
                    itemMillis >= sevenDaysAgo
                }
                else -> true
            }
        }
    }
}

sealed class TransactionListUiEvent : UiEvent() {
    data class OnFilterTypeChanged(val filterType: String) : TransactionListUiEvent()
    data class OnDateRangeChanged(val dateRange: String) : TransactionListUiEvent()
    data object OnBackPressed : TransactionUiEvent()
}
open class TransactionUiEvent : UiEvent()
