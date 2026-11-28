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

        override suspend fun syncAllCloudData(userId: String) {
            // no-op in test fake
        }

        override suspend fun clearCurrentUserData() {
            listFlow.value = emptyList()
        }

        private val userBalanceFlow = MutableStateFlow<com.waleed.vaulta.android.data.model.UserBalanceEntity?>(null)
        override fun observeUserBalance(userId: String): Flow<com.waleed.vaulta.android.data.model.UserBalanceEntity?> = userBalanceFlow
        override suspend fun getUserBalance(userId: String): com.waleed.vaulta.android.data.model.UserBalanceEntity? = userBalanceFlow.value
        override suspend fun recomputeAndPersistBalance(userId: String?) {}

        private val sharedSplitsFlow = MutableStateFlow<List<com.waleed.vaulta.android.data.model.SharedSplitEntity>>(emptyList())
        override fun getAllSharedSplits(): Flow<List<com.waleed.vaulta.android.data.model.SharedSplitEntity>> = sharedSplitsFlow
        override suspend fun insertSharedSplit(split: com.waleed.vaulta.android.data.model.SharedSplitEntity): Boolean = true
        override suspend fun insertSharedSplits(splits: List<com.waleed.vaulta.android.data.model.SharedSplitEntity>): Boolean = true
        override suspend fun updateSharedSplit(split: com.waleed.vaulta.android.data.model.SharedSplitEntity): Boolean = true
        override suspend fun settleSharedSplit(id: String): Boolean = true
        override suspend fun deleteSharedSplit(id: String): Boolean = true
        override suspend fun syncSharedSplits() {}
        override suspend fun lookupVaultaContact(query: String): com.waleed.vaulta.android.data.model.ContactLookupResult =
            com.waleed.vaulta.android.data.model.ContactLookupResult(false)
        override suspend fun searchVaultaUsers(query: String): List<com.waleed.vaulta.android.data.model.VaultaUserSearchResult> = emptyList()
        override suspend fun setIsSearchable(isSearchable: Boolean): Boolean = true

        private val creditOweFlow = MutableStateFlow<List<com.waleed.vaulta.android.data.model.CreditOweEntity>>(emptyList())
        override fun getAllCreditOwe(): Flow<List<com.waleed.vaulta.android.data.model.CreditOweEntity>> = creditOweFlow
        override suspend fun getCreditOweById(id: String): com.waleed.vaulta.android.data.model.CreditOweEntity? = null
        override fun getUnifiedTransactions(): Flow<List<ExpenseEntity>> = listFlow
        override suspend fun insertCreditOwe(entry: com.waleed.vaulta.android.data.model.CreditOweEntity): Boolean = true
        override suspend fun updateCreditOwe(entry: com.waleed.vaulta.android.data.model.CreditOweEntity): Boolean = true
        override suspend fun settleCreditOwe(id: String): Boolean = true
        override suspend fun deleteCreditOwe(id: String): Boolean = true
        override suspend fun syncCreditOwe() {}
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

    @Test
    fun testUpiCounterpartyExtractionPatterns() {
        // 1. Sent to Person
        val p1 = TransactionNotificationParser.parse(
            packageName = "com.google.android.apps.messaging",
            title = "Axis Bank",
            text = "You sent Rs.500 to Rahul Sharma on 24-Nov-26. Ref: 12345"
        )
        assertNotNull(p1)
        assertEquals("Expense", p1!!.type)
        assertEquals(500.0, p1.amount, 0.01)
        assertEquals("Paid to Rahul Sharma", p1.title)

        // 2. Paid to Merchant
        val p2 = TransactionNotificationParser.parse(
            packageName = "com.google.android.apps.messaging",
            title = "HDFC Bank",
            text = "You paid ₹250 to Swiggy on 24-Nov-26. Avl bal: Rs 5,000"
        )
        assertNotNull(p2)
        assertEquals("Expense", p2!!.type)
        assertEquals(250.0, p2.amount, 0.01)
        assertEquals("Paid to Swiggy", p2.title)

        // 3. Debited to VPA
        val p3 = TransactionNotificationParser.parse(
            packageName = "com.google.android.apps.messaging",
            title = "ICICI Bank",
            text = "Your a/c XX123 is debited for INR 150.00 to VPA xyz@okicici on 24-Nov-26"
        )
        assertNotNull(p3)
        assertEquals("Expense", p3!!.type)
        assertEquals(150.0, p3.amount, 0.01)
        assertEquals("Paid to xyz@okicici", p3.title)

        // 4. Received from Person
        val p4 = TransactionNotificationParser.parse(
            packageName = "com.google.android.apps.messaging",
            title = "SBI",
            text = "You received Rs.1,000 from Amit Kumar on 24-Nov-26. Avl bal: Rs 10,000"
        )
        assertNotNull(p4)
        assertEquals("Income", p4!!.type)
        assertEquals(1000.0, p4.amount, 0.01)
        assertEquals("Received from Amit Kumar", p4.title)

        // 5. Credited from VPA
        val p5 = TransactionNotificationParser.parse(
            packageName = "com.google.android.apps.messaging",
            title = "Paytm",
            text = "Your account is credited with Rs 300.00 from VPA abc@ybl on 24-Nov-26"
        )
        assertNotNull(p5)
        assertEquals("Income", p5!!.type)
        assertEquals(300.0, p5.amount, 0.01)
        assertEquals("Received from abc@ybl", p5.title)
    }
}
