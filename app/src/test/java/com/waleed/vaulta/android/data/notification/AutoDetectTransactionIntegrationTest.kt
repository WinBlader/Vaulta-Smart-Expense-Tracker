package com.waleed.vaulta.android.data.notification

import com.waleed.vaulta.android.data.model.ExpenseEntity
import com.waleed.vaulta.android.data.model.ExpenseSummary
import com.waleed.vaulta.android.data.repository.ExpenseRepository
import com.waleed.vaulta.android.utils.Utils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoDetectTransactionIntegrationTest {

    class FakeExpenseRepository : ExpenseRepository {
        private val listFlow = MutableStateFlow<List<ExpenseEntity>>(emptyList())

        override fun getAllExpenses(): Flow<List<ExpenseEntity>> = listFlow
        override fun getTopExpenses(): Flow<List<ExpenseEntity>> = listFlow
        override fun getTopIncomes(): Flow<List<ExpenseEntity>> = listFlow
        override fun getTopCreditOwe(): Flow<List<ExpenseEntity>> = listFlow
        override fun getAllExpenseByDate(type: String): Flow<List<ExpenseSummary>> = MutableStateFlow(emptyList())
        override fun getCreditOweExpenseByDate(): Flow<List<ExpenseSummary>> = MutableStateFlow(emptyList())

        override suspend fun insertExpense(expense: ExpenseEntity): Boolean {
            val current = listFlow.value.toMutableList()
            current.add(0, expense.copy(id = current.size + 1))
            listFlow.value = current
            return true
        }

        override suspend fun updateExpense(expense: ExpenseEntity): Boolean {
            val current = listFlow.value.toMutableList()
            val index = current.indexOfFirst { it.id == expense.id }
            if (index >= 0) {
                current[index] = expense
                listFlow.value = current
                return true
            }
            return false
        }

        override suspend fun deleteExpense(expense: ExpenseEntity): Boolean {
            val current = listFlow.value.toMutableList()
            current.removeAll { it.id == expense.id }
            listFlow.value = current
            return true
        }

        override suspend fun syncUserTransactions() {
            // no-op in test fake
        }

        override suspend fun clearCurrentUserData() {
            listFlow.value = emptyList()
        }
    }

    @Test
    fun testAutoDetectedTransactionFlowsDirectlyToRepositoryFlow() = runBlocking {
        val fakeRepository = FakeExpenseRepository()

        // 1. Initially, repository transaction list is empty
        val initialList = fakeRepository.getAllExpenses().first()
        assertEquals(0, initialList.size)

        // 2. Incoming bank SMS alert arrives from messaging app and is parsed
        val rawSms = "HDFC Bank: Rs 850.00 debited from a/c **1234 on 23-11-26 at Zomato. Avl bal: Rs 18,200.00"
        val parsed = TransactionNotificationParser.parse(
            packageName = "com.google.android.apps.messaging",
            title = "HDFC Bank",
            text = rawSms
        )
        assertNotNull(parsed)
        assertEquals("Expense", parsed!!.type)
        assertEquals(850.0, parsed.amount, 0.01)
        assertEquals("Food", parsed.category)
        assertTrue(parsed.title.contains("Zomato", ignoreCase = true))

        // 3. Service attaches the auto-detected transaction under the active user/guest key
        val userKey = "test_user_account"
        val autoDetectedEntity = ExpenseEntity(
            id = null,
            title = parsed.title,
            amount = parsed.amount,
            date = Utils.formatDateToHumanReadableForm(parsed.timestamp),
            type = parsed.type,
            userId = userKey
        )
        val inserted = fakeRepository.insertExpense(autoDetectedEntity)
        assertTrue(inserted)

        // 4. Repository Flow updates immediately for Dashboard and Transaction List consumers
        val updatedList = fakeRepository.getAllExpenses().first()
        assertEquals(1, updatedList.size)

        val firstItem = updatedList.first()
        assertEquals(850.0, firstItem.amount, 0.01)
        assertEquals("Expense", firstItem.type)
        assertEquals(userKey, firstItem.userId)
        assertTrue(firstItem.title.contains("Zomato", ignoreCase = true))
    }

    @Test
    fun testAutoDetectedIncomeTransactionFlow() = runBlocking {
        val fakeRepository = FakeExpenseRepository()

        // Incoming bank deposit SMS (e.g. Navi / Salary / UPI transfer)
        val rawSms = "NAVI LIMITED Deposited IN YOUR ICICI BANK Account XX123 INR 5,000.00 on 23-Nov-26. Avl Bal: INR 25,000.00"
        val parsed = TransactionNotificationParser.parse(
            packageName = "com.google.android.apps.messaging",
            title = "ICICI Bank",
            text = rawSms
        )
        assertNotNull(parsed)
        assertEquals("Income", parsed!!.type)
        assertEquals(5000.0, parsed.amount, 0.01)

        val userKey = "test_user_account"
        val autoDetectedEntity = ExpenseEntity(
            id = null,
            title = parsed.title,
            amount = parsed.amount,
            date = Utils.formatDateToHumanReadableForm(parsed.timestamp),
            type = parsed.type,
            userId = userKey
        )
        val inserted = fakeRepository.insertExpense(autoDetectedEntity)
        assertTrue(inserted)

        val updatedList = fakeRepository.getAllExpenses().first()
        assertEquals(1, updatedList.size)
        assertEquals("Income", updatedList.first().type)
        assertEquals(5000.0, updatedList.first().amount, 0.01)
    }

    @Test
    fun testIncomingP2pPaidYouFlow() = runBlocking {
        val fakeRepository = FakeExpenseRepository()

        val rawNotification = "AJAZ Ahmad WANI paid you ₹101.00"
        val parsed = TransactionNotificationParser.parse(
            packageName = "com.google.android.apps.messaging",
            title = "Google Messages",
            text = rawNotification
        )
        assertNotNull(parsed)
        assertEquals("Income", parsed!!.type)
        assertEquals(101.0, parsed.amount, 0.01)
        assertTrue(parsed.title.contains("AJAZ Ahmad WANI", ignoreCase = true))

        val autoDetectedEntity = ExpenseEntity(
            id = null,
            title = parsed.title,
            amount = parsed.amount,
            date = Utils.formatDateToHumanReadableForm(parsed.timestamp),
            type = parsed.type,
            userId = "test_user_key"
        )
        val inserted = fakeRepository.insertExpense(autoDetectedEntity)
        assertTrue(inserted)

        val updated = fakeRepository.getAllExpenses().first()
        assertEquals(1, updated.size)
        assertEquals("Income", updated.first().type)
        assertEquals(101.0, updated.first().amount, 0.01)
        assertTrue(updated.first().title.contains("AJAZ Ahmad WANI", ignoreCase = true))
    }
}
