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
    val secondaryEntries: List<Entry> = emptyList(), // For Credit/Owe dual chart (You Owe vs Owed to You)
    val labels: List<String> = emptyList(),
    val totalAmount: Double = 0.0,
    val secondaryTotalAmount: Double = 0.0
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository
) : BaseViewModel() {

    private val _selectedType = MutableStateFlow("Expense")
    val selectedType: StateFlow<String> = _selectedType.asStateFlow()

    val entries = _selectedType.flatMapLatest { type ->
        if (type == "Credit") {
            expenseRepository.getCreditOweExpenseByDate()
        } else {
            expenseRepository.getAllExpenseByDate(type)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val topEntries: StateFlow<List<ExpenseEntity>> = _selectedType.flatMapLatest { type ->
        when (type) {
            "Income" -> expenseRepository.getTopIncomes()
            "Credit" -> expenseRepository.getTopCreditOwe()
            else -> expenseRepository.getTopExpenses()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onTypeSelected(type: String) {
        _selectedType.value = type
    }

    fun getChartData(rawSummaries: List<ExpenseSummary>): ChartData {
        if (rawSummaries.isEmpty()) return ChartData()

        val type = _selectedType.value
        if (type == "Credit") {
            // Segregate by type: "You Owe" vs "Owed to You"
            val oweSummaries = rawSummaries.filter { it.type.equals("You Owe", ignoreCase = true) }
            val owedSummaries = rawSummaries.filter { it.type.equals("Owed to You", ignoreCase = true) }

            // Collect all unique dates sorted
            val allDates = rawSummaries.map { it.date }.distinct().sortedBy { Utils.getMillisFromDate(it) }
            val labels = mutableListOf<String>()
            val oweEntries = mutableListOf<Entry>()
            val owedEntries = mutableListOf<Entry>()
            var totalOwe = 0.0
            var totalOwed = 0.0

            allDates.forEachIndexed { index, dateStr ->
                val millis = Utils.getMillisFromDate(dateStr)
                labels.add(Utils.formatDayMonth(millis))

                val oweAmount = oweSummaries.filter { it.date == dateStr }.sumOf { if (it.total_amount.isNaN()) 0.0 else it.total_amount.coerceAtLeast(0.0) }
                val owedAmount = owedSummaries.filter { it.date == dateStr }.sumOf { if (it.total_amount.isNaN()) 0.0 else it.total_amount.coerceAtLeast(0.0) }

                oweEntries.add(Entry(index.toFloat(), oweAmount.toFloat()))
                owedEntries.add(Entry(index.toFloat(), owedAmount.toFloat()))

                totalOwe += oweAmount
                totalOwed += owedAmount
            }

            return ChartData(
                entries = oweEntries,
                secondaryEntries = owedEntries,
                labels = labels,
                totalAmount = if (totalOwe.isNaN()) 0.0 else totalOwe.coerceAtLeast(0.0),
                secondaryTotalAmount = if (totalOwed.isNaN()) 0.0 else totalOwed.coerceAtLeast(0.0)
            )
        }

        // Sort chronologically by timestamp
        val sorted = rawSummaries.sortedBy { Utils.getMillisFromDate(it.date) }
        val entries = mutableListOf<Entry>()
        val labels = mutableListOf<String>()
        var total = 0.0

        sorted.forEachIndexed { index, summary ->
            val amt = if (summary.total_amount.isNaN()) 0.0 else summary.total_amount.coerceAtLeast(0.0)
            entries.add(Entry(index.toFloat(), amt.toFloat()))
            val millis = Utils.getMillisFromDate(summary.date)
            labels.add(Utils.formatDayMonth(millis))
            total += amt
        }

        return ChartData(
            entries = entries,
            labels = labels,
            totalAmount = if (total.isNaN()) 0.0 else total.coerceAtLeast(0.0)
        )
    }

    override fun onEvent(event: UiEvent) {
    }
}
