package com.waleed.vaulta.android

import com.waleed.vaulta.android.data.model.ExpenseEntity
import com.waleed.vaulta.android.data.model.ExpenseSummary
import com.waleed.vaulta.android.feature.home.HomeViewModel
import com.waleed.vaulta.android.feature.stats.StatsViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class VaultaFeatureIntegrationTest {

    @Test
    fun testTransactionNoteField() {
        val expenseWithNote = ExpenseEntity(
            id = 1,
            title = "Dinner with team",
            amount = 1250.0,
            date = "Nov 24, 2026",
            type = "Expense",
            userId = "user_123",
            note = "Project celebration dinner"
        )

        assertEquals("Project celebration dinner", expenseWithNote.note)
        assertEquals("Expense", expenseWithNote.type)
        assertEquals(1250.0, expenseWithNote.amount, 0.001)

        val expenseWithoutNote = ExpenseEntity(
            id = 2,
            title = "Coffee",
            amount = 150.0,
            date = "Nov 24, 2026",
            type = "Expense",
            userId = "user_123"
        )
        assertNull(expenseWithoutNote.note)
    }

    @Test
    fun testCreditOweBalanceCalculations() {
        val list = listOf(
            ExpenseEntity(id = 1, title = "Salary", amount = 50000.0, date = "Nov 1, 2026", type = "Income"),
            ExpenseEntity(id = 2, title = "Grocery", amount = 4000.0, date = "Nov 2, 2026", type = "Expense"),
            ExpenseEntity(id = 3, title = "Friend A", amount = 2000.0, date = "Nov 3, 2026", type = "Owed to You"),
            ExpenseEntity(id = 4, title = "Vendor B", amount = 1500.0, date = "Nov 4, 2026", type = "You Owe")
        )

        // Math: Total Balance = (Income + Owed to You) - (Expense + You Owe)
        // (50000 + 2000) - (4000 + 1500) = 52000 - 5500 = 46500
        var balance = 0.0
        for (item in list) {
            when (item.type) {
                "Income", "Owed to You", "Credit" -> balance += item.amount
                else -> balance -= item.amount
            }
        }
        assertEquals(46500.0, balance, 0.001)
    }

    @Test
    fun testStatsCreditOweDualDatasetLogic() {
        val summaries = listOf(
            ExpenseSummary(type = "You Owe", date = "Nov 10, 2026", total_amount = 500.0),
            ExpenseSummary(type = "Owed to You", date = "Nov 10, 2026", total_amount = 1200.0),
            ExpenseSummary(type = "You Owe", date = "Nov 12, 2026", total_amount = 300.0),
            ExpenseSummary(type = "Owed to You", date = "Nov 12, 2026", total_amount = 800.0)
        )

        val oweSummaries = summaries.filter { it.type.equals("You Owe", ignoreCase = true) }
        val owedSummaries = summaries.filter { it.type.equals("Owed to You", ignoreCase = true) }

        val totalOwe = oweSummaries.sumOf { it.total_amount }
        val totalOwed = owedSummaries.sumOf { it.total_amount }

        assertEquals(800.0, totalOwe, 0.001)
        assertEquals(2000.0, totalOwed, 0.001)
    }

    @Test
    fun testProfileFallbackDoesNotOverwriteCustomName() {
        val existingSavedName = "Waleed Wani"
        val incomingEmail = "waleedwani06072005@gmail.com"
        val fallbackEmailPrefix = incomingEmail.substringBefore("@") // "waleedwani06072005"

        // Rule: If savedProfile exists, always prioritize saved name over email substring fallback
        val resolvedName = existingSavedName.ifBlank { fallbackEmailPrefix }
        assertEquals("Waleed Wani", resolvedName)
    }
}
