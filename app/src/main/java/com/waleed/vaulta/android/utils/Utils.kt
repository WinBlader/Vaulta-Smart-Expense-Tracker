package com.waleed.vaulta.android.utils

import com.waleed.vaulta.android.R
import com.waleed.vaulta.android.data.CurrencyManager
import com.waleed.vaulta.android.data.model.CurrencyItem
import com.waleed.vaulta.android.data.model.ExpenseEntity
import java.text.SimpleDateFormat
import java.util.Locale

object Utils {

    fun formatDateToHumanReadableForm(dateInMillis: Long): String {
        val validMillis = if (dateInMillis <= 0L) System.currentTimeMillis() else dateInMillis
        val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        return dateFormatter.format(validMillis)
    }

    fun formatDateForChart(dateInMillis: Long): String {
        val validMillis = if (dateInMillis <= 0L) System.currentTimeMillis() else dateInMillis
        val dateFormatter = SimpleDateFormat("dd-MMM", Locale.getDefault())
        return dateFormatter.format(validMillis)
    }

    fun formatCurrency(amount: Double, currency: CurrencyItem = CurrencyManager.getSelectedCurrency()): String {
        return CurrencyManager.formatAmount(amount, currency)
    }

    fun formatDayMonthYear(dateInMillis: Long): String {
        val validMillis = if (dateInMillis <= 86400000L) System.currentTimeMillis() else dateInMillis
        val dateFormatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        return dateFormatter.format(validMillis)
    }

    fun formatDayMonth(dateInMillis: Long): String {
        val validMillis = if (dateInMillis <= 86400000L) System.currentTimeMillis() else dateInMillis
        val dateFormatter = SimpleDateFormat("dd/MMM", Locale.getDefault())
        return dateFormatter.format(validMillis)
    }

    fun formatToDecimalValue(d: Double): String {
        return String.format(Locale.US, "%.2f", d)
    }

    fun formatStringDateToMonthDayYear(date: String): String {
        val millis = getMillisFromDate(date)
        return formatDayMonthYear(millis)
    }

    fun getMillisFromDate(date: String): Long {
        return getMilliFromDate(date)
    }

    fun getMilliFromDate(dateFormat: String?): Long {
        if (dateFormat.isNullOrBlank()) return System.currentTimeMillis()
        val formats = listOf(
            "dd/MM/yyyy",
            "dd/MM/YYYY",
            "MMM dd, yyyy",
            "yyyy-MM-dd"
        )
        for (pattern in formats) {
            try {
                val formatter = SimpleDateFormat(pattern, Locale.getDefault()).apply {
                    isLenient = false
                }
                val parsed = formatter.parse(dateFormat.trim())
                if (parsed != null && parsed.time > 86400000L) {
                    return parsed.time
                }
            } catch (e: Exception) {
                // Try next pattern
            }
        }
        return System.currentTimeMillis()
    }

    fun getItemIcon(item: ExpenseEntity): Int {
        val titleLower = item.title.lowercase()
        return when {
            titleLower.contains("paypal") -> R.drawable.money_icon
            titleLower.contains("netflix") -> R.drawable.money_icon
            titleLower.contains("starbucks") -> R.drawable.money_icon
            titleLower.contains("upwork") || titleLower.contains("freelance") -> R.drawable.money_icon
            titleLower.contains("youtube") -> R.drawable.money_icon
            else -> R.drawable.money_icon
        }
    }
}
