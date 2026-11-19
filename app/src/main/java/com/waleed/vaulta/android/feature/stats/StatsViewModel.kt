package com.waleed.vaulta.android.feature.stats

import androidx.lifecycle.viewModelScope
import com.waleed.vaulta.android.base.BaseViewModel
import com.waleed.vaulta.android.base.UiEvent
import com.waleed.vaulta.android.data.model.ExpenseEntity
import com.waleed.vaulta.android.data.model.ExpenseSummary
import com.waleed.vaulta.android.data.repository.ExpenseRepository
import com.waleed.vaulta.android.utils.Utils
import com.github.mikephil.charting.data.Entry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ChartData(
    val entries: List<Entry> = emptyList(),
    val labels: List<String> = emptyList(),
    val totalAmount: Double = 0.0
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository
) : BaseViewModel() {

    private val _selectedType = MutableStateFlow("Expense")
    val selectedType: StateFlow<String> = _selectedType.asStateFlow()

    val entries = _selectedType.flatMapLatest { type ->
        expenseRepository.getAllExpenseByDate(type)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val topEntries: StateFlow<List<ExpenseEntity>> = expenseRepository.getTopExpenses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onTypeSelected(type: String) {
        _selectedType.value = type
    }

    fun getChartData(rawSummaries: List<ExpenseSummary>): ChartData {
        if (rawSummaries.isEmpty()) return ChartData()

        // Sort chronologically by timestamp
        val sorted = rawSummaries.sortedBy { Utils.getMillisFromDate(it.date) }
        val entries = mutableListOf<Entry>()
        val labels = mutableListOf<String>()
        var total = 0.0

        sorted.forEachIndexed { index, summary ->
            entries.add(Entry(index.toFloat(), summary.total_amount.toFloat()))
            val millis = Utils.getMillisFromDate(summary.date)
            labels.add(Utils.formatDayMonth(millis))
            total += summary.total_amount
        }

        return ChartData(
            entries = entries,
            labels = labels,
            totalAmount = total
        )
    }

    override fun onEvent(event: UiEvent) {
    }
}
