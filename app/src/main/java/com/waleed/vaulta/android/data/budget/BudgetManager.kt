package com.waleed.vaulta.android.data.budget

import android.content.Context
import android.content.SharedPreferences
import com.waleed.vaulta.android.data.model.ExpenseEntity
import com.waleed.vaulta.android.utils.Utils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BudgetManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationHelper: BudgetNotificationHelper
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("vaulta_budget_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_MONTHLY_BUDGET = "monthly_budget_amount"
        private const val KEY_LAST_ALERT_LEVEL = "budget_last_alert_level" // 0 = none, 1 = warning (80%), 2 = exceeded (100%)
        private const val KEY_LAST_ALERT_MONTH = "budget_last_alert_month"
        private const val KEY_LAST_NEGATIVE_BALANCE_NOTIFIED = "budget_last_negative_balance_notified"
    }

    private val _monthlyBudget = MutableStateFlow(loadBudgetFromPrefs())
    val monthlyBudget: StateFlow<Double> = _monthlyBudget.asStateFlow()

    private fun loadBudgetFromPrefs(): Double {
        return prefs.getString(KEY_MONTHLY_BUDGET, "0.0")?.toDoubleOrNull() ?: 0.0
    }

    private fun getCurrentMonthKey(): String {
        val cal = Calendar.getInstance()
        return "${cal.get(Calendar.MONTH)}-${cal.get(Calendar.YEAR)}"
    }

    private fun getSavedAlertLevel(): Int {
        val savedMonth = prefs.getString(KEY_LAST_ALERT_MONTH, "") ?: ""
        if (savedMonth != getCurrentMonthKey()) {
            // New month started, reset alert level
            prefs.edit()
                .putString(KEY_LAST_ALERT_MONTH, getCurrentMonthKey())
                .putInt(KEY_LAST_ALERT_LEVEL, 0)
                .apply()
            return 0
        }
        return prefs.getInt(KEY_LAST_ALERT_LEVEL, 0)
    }

    private fun saveAlertLevel(level: Int) {
        prefs.edit()
            .putString(KEY_LAST_ALERT_MONTH, getCurrentMonthKey())
            .putInt(KEY_LAST_ALERT_LEVEL, level)
            .apply()
    }

    fun getMonthlyBudget(): Double {
        return _monthlyBudget.value
    }

    fun setMonthlyBudget(amount: Double, currentExpenses: List<ExpenseEntity> = emptyList()) {
        val validAmount = amount.coerceAtLeast(0.0)
        prefs.edit()
            .putString(KEY_MONTHLY_BUDGET, validAmount.toString())
            .putInt(KEY_LAST_ALERT_LEVEL, 0) // Reset alert level so new budget is evaluated fresh
            .putString(KEY_LAST_ALERT_MONTH, getCurrentMonthKey())
            .apply()
        _monthlyBudget.value = validAmount

        if (currentExpenses.isNotEmpty() && validAmount > 0.0) {
            checkBudgetAndNotify(currentExpenses)
        }
    }

    fun calculateCurrentMonthSpends(expenses: List<ExpenseEntity>): Double {
        val cal = Calendar.getInstance()
        val currentMonth = cal.get(Calendar.MONTH)
        val currentYear = cal.get(Calendar.YEAR)

        var total = 0.0
        val expCal = Calendar.getInstance()

        for (expense in expenses) {
            if (expense.type != "Income") {
                val millis = Utils.getMillisFromDate(expense.date)
                expCal.timeInMillis = millis
                if (expCal.get(Calendar.MONTH) == currentMonth && expCal.get(Calendar.YEAR) == currentYear) {
                    total += expense.amount
                }
            }
        }
        // If no expenses matched the exact month (e.g. mock data or unparsed dates), fall back to summing all Expense entries
        if (total == 0.0) {
            for (expense in expenses) {
                if (expense.type != "Income") {
                    total += expense.amount
                }
            }
        }
        return total
    }

    /**
     * Checks current month expenses against budget and triggers high-priority warning/exceeded notifications.
     */
    fun checkBudgetAndNotify(expenses: List<ExpenseEntity>) {
        val budget = _monthlyBudget.value
        if (budget <= 0.0) return

        val currentSpend = calculateCurrentMonthSpends(expenses)
        val percent = (currentSpend / budget) * 100.0
        val lastLevel = getSavedAlertLevel()

        if (percent >= 100.0) {
            if (lastLevel < 2) {
                notificationHelper.sendBudgetExceededNotification(currentSpend, budget)
                saveAlertLevel(2)
            }
        } else if (percent >= 80.0) {
            if (lastLevel < 1) {
                notificationHelper.sendBudgetWarningNotification(currentSpend, budget)
                saveAlertLevel(1)
            }
        } else {
            if (lastLevel != 0) {
                saveAlertLevel(0)
            }
        }
    }

    /**
     * Checks if total balance is negative and sends a high-priority warning notification to the user.
     */
    fun checkBalanceAndNotify(expenses: List<ExpenseEntity>, forceNotifyIfNegative: Boolean = false) {
        if (expenses.isEmpty()) return

        var totalIncome = 0.0
        var totalExpense = 0.0
        for (expense in expenses) {
            if (expense.type == "Income") {
                totalIncome += expense.amount
            } else {
                totalExpense += expense.amount
            }
        }
        val currentBalance = totalIncome - totalExpense
        val wasNegativeNotified = prefs.getBoolean(KEY_LAST_NEGATIVE_BALANCE_NOTIFIED, false)

        if (currentBalance < 0.0) {
            if (!wasNegativeNotified || forceNotifyIfNegative) {
                notificationHelper.sendNegativeBalanceNotification(currentBalance, totalIncome, totalExpense)
                prefs.edit().putBoolean(KEY_LAST_NEGATIVE_BALANCE_NOTIFIED, true).apply()
            }
        } else {
            // Balance recovered back to zero or positive, reset flag so user is notified if it drops again
            if (wasNegativeNotified) {
                prefs.edit().putBoolean(KEY_LAST_NEGATIVE_BALANCE_NOTIFIED, false).apply()
            }
        }
    }
}
