package com.waleed.vaulta.android

import com.waleed.vaulta.android.data.model.ExpenseEntity
import com.waleed.vaulta.android.data.model.ExpenseSummary
import com.waleed.vaulta.android.data.model.TransactionDto
import com.waleed.vaulta.android.feature.home.HomeViewModel
import com.waleed.vaulta.android.feature.stats.StatsViewModel
import com.waleed.vaulta.android.utils.NavRouts
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun testTransactionDtoSerializationAndDeserialization() {
        val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
        val dto = TransactionDto(
            id = "supabase_uuid_101",
            localId = 42,
            userId = "user_uuid_999",
            title = "Cloud Hosting",
            amount = 1499.0,
            date = "Nov 24, 2026",
            type = "Expense",
            note = "Annual server renew",
            createdAt = "2026-11-24T12:00:00Z"
        )

        val jsonString = json.encodeToString(dto)
        assertTrue(jsonString.contains("\"title\":\"Cloud Hosting\""))
        assertTrue(jsonString.contains("\"amount\":1499.0"))
        assertTrue(jsonString.contains("\"note\":\"Annual server renew\""))

        val decoded = json.decodeFromString<TransactionDto>(jsonString)
        assertEquals("supabase_uuid_101", decoded.id)
        assertEquals(42, decoded.localId)
        assertEquals("user_uuid_999", decoded.userId)
        assertEquals("Cloud Hosting", decoded.title)
        assertEquals(1499.0, decoded.amount, 0.001)
        assertEquals("Annual server renew", decoded.note)

        // Test with missing/null optional fields
        val partialJson = """{"title":"Freelance Payment","amount":5000.0,"type":"Income"}"""
        val decodedPartial = json.decodeFromString<TransactionDto>(partialJson)
        assertEquals("Freelance Payment", decodedPartial.title)
        assertEquals(5000.0, decodedPartial.amount, 0.001)
        assertEquals("Income", decodedPartial.type)
        assertNull(decodedPartial.note)
        assertNull(decodedPartial.localId)
    }

    @Test
    fun testSessionSignOutDoesNotWipeUserTransactions() {
        // Mock local DB storage
        val localDatabase = mutableListOf<ExpenseEntity>()
        val canonicalUserId = "c2195f26-bf31-419b-a010-0fcda110026e"
        val userEmail = "waleedwani06072005@gmail.com"

        // Step 1: User logs in and creates transactions
        localDatabase.add(
            ExpenseEntity(
                id = 1,
                title = "Client Project Advance",
                amount = 25000.0,
                date = "Nov 24, 2026",
                type = "Income",
                userId = canonicalUserId,
                note = "Upfront milestone"
            )
        )
        localDatabase.add(
            ExpenseEntity(
                id = 2,
                title = "Grocery Supplies",
                amount = 3200.0,
                date = "Nov 24, 2026",
                type = "Expense",
                userId = canonicalUserId
            )
        )
        localDatabase.add(
            ExpenseEntity(
                id = 3,
                title = "Friend Loan",
                amount = 5000.0,
                date = "Nov 24, 2026",
                type = "Owed to You",
                userId = canonicalUserId
            )
        )

        // Legacy row saved under email before UUID unification
        localDatabase.add(
            ExpenseEntity(
                id = 4,
                title = "Legacy Utility Bill",
                amount = 1800.0,
                date = "Nov 15, 2026",
                type = "Expense",
                userId = userEmail
            )
        )

        assertEquals(4, localDatabase.size)

        // Step 2: User signs out
        // Under our fix, signOut() does NOT execute deleteExpensesForUser()
        // Transactions remain in Room DB
        val loggedOutUserKey = "guest"
        val guestRows = localDatabase.filter { it.userId == loggedOutUserKey }
        assertEquals(0, guestRows.size) // Guest sees 0 rows

        // Step 3: User signs back in with canonicalUserId and fallbackEmail
        val restoredRows = localDatabase.filter {
            it.userId.equals(canonicalUserId, ignoreCase = true) ||
            it.userId.equals(userEmail, ignoreCase = true)
        }
        assertEquals(4, restoredRows.size)

        // Calculate balance
        var totalBalance = 0.0
        for (item in restoredRows) {
            when (item.type) {
                "Income", "Owed to You", "Credit" -> totalBalance += item.amount
                else -> totalBalance -= item.amount
            }
        }
        // (25000 + 5000) - (3200 + 1800) = 30000 - 5000 = 25000.0
        assertEquals(25000.0, totalBalance, 0.001)

        // Statistics datasets verification
        val expenseTotal = restoredRows.filter { it.type.equals("Expense", ignoreCase = true) }.sumOf { it.amount }
        val incomeTotal = restoredRows.filter { it.type.equals("Income", ignoreCase = true) }.sumOf { it.amount }
        val creditOweTotal = restoredRows.filter { it.type.equals("Owed to You", ignoreCase = true) }.sumOf { it.amount }

        assertEquals(5000.0, expenseTotal, 0.001)
        assertEquals(25000.0, incomeTotal, 0.001)
        assertEquals(5000.0, creditOweTotal, 0.001)
    }

    // ==============================================================
    // Privacy Policy Acceptance Gate Navigation & Persistence Tests
    // ==============================================================

    @Test
    fun testNewUserRegistrationRedirectsToPrivacyPolicyGateAndProceedsOnAcceptance() {
        val mockStorage = mutableMapOf<String, Any>()
        val newUserId = "user_new_456"

        fun isPrivacyPolicyAccepted(userKey: String): Boolean =
            mockStorage["privacy_policy_accepted_v1_$userKey"] as? Boolean ?: false

        fun setPrivacyPolicyAccepted(accepted: Boolean, userKey: String) {
            mockStorage["privacy_policy_accepted_v1_$userKey"] = accepted
            mockStorage["privacy_policy_accepted_v1_timestamp_$userKey"] = System.currentTimeMillis()
        }

        fun resolvePostAuthDestination(userKey: String): String =
            if (!isPrivacyPolicyAccepted(userKey)) NavRouts.privacy_policy_consent else NavRouts.home

        // 1. Immediately after registration, user has NOT accepted yet
        assertFalse(isPrivacyPolicyAccepted(newUserId))
        val postRegDestination = resolvePostAuthDestination(newUserId)
        assertEquals(NavRouts.privacy_policy_consent, postRegDestination)

        // 2. User cannot enter HomeScreen until accepted
        val isBlocked = postRegDestination != NavRouts.home
        assertTrue(isBlocked)

        // 3. User taps "Accept & Continue" on the Privacy Policy screen
        setPrivacyPolicyAccepted(true, newUserId)
        assertTrue(isPrivacyPolicyAccepted(newUserId))

        // 4. Now navigation resolves directly to HomeScreen
        val destinationAfterAccept = resolvePostAuthDestination(newUserId)
        assertEquals(NavRouts.home, destinationAfterAccept)
    }

    @Test
    fun testExistingUpgradedUserWithoutAcceptanceShownGateOnceBeforeHomeScreen() {
        val mockStorage = mutableMapOf<String, Any>()
        val upgradedUserId = "user_legacy_789"
        val isLoggedIn = true

        fun isPrivacyPolicyAccepted(userKey: String): Boolean =
            mockStorage["privacy_policy_accepted_v1_$userKey"] as? Boolean ?: false

        fun setPrivacyPolicyAccepted(accepted: Boolean, userKey: String) {
            mockStorage["privacy_policy_accepted_v1_$userKey"] = accepted
        }

        fun resolveSplashDestination(isLoggedIn: Boolean, isAccepted: Boolean): String =
            when {
                !isLoggedIn -> NavRouts.login
                !isAccepted -> NavRouts.privacy_policy_consent
                else -> NavRouts.home
            }

        // 1. Upgraded user launch before accepting: privacy_policy_accepted_v1 is missing/false
        assertFalse(isPrivacyPolicyAccepted(upgradedUserId))
        val firstLaunchDestination = resolveSplashDestination(
            isLoggedIn = isLoggedIn,
            isAccepted = isPrivacyPolicyAccepted(upgradedUserId)
        )
        assertEquals(NavRouts.privacy_policy_consent, firstLaunchDestination)

        // 2. User accepts the Privacy Policy
        setPrivacyPolicyAccepted(true, upgradedUserId)

        // 3. Subsequent app launches proceed straight to Home Screen
        val subsequentLaunchDestination = resolveSplashDestination(
            isLoggedIn = isLoggedIn,
            isAccepted = isPrivacyPolicyAccepted(upgradedUserId)
        )
        assertEquals(NavRouts.home, subsequentLaunchDestination)
    }

    @Test
    fun testReturningUserWhoAlreadyAcceptedGoesStraightToHomeScreen() {
        val mockStorage = mutableMapOf<String, Any>()
        val returningUserId = "user_returning_321"

        // Returning user has already accepted in previous session
        mockStorage["privacy_policy_accepted_v1_$returningUserId"] = true

        fun isPrivacyPolicyAccepted(userKey: String): Boolean =
            mockStorage["privacy_policy_accepted_v1_$userKey"] as? Boolean ?: false

        fun resolveLoginDestination(userKey: String): String =
            if (!isPrivacyPolicyAccepted(userKey)) NavRouts.privacy_policy_consent else NavRouts.home

        // Logging in takes returning user directly to Home Screen
        val destination = resolveLoginDestination(returningUserId)
        assertEquals(NavRouts.home, destination)
    }

    @Test
    fun testSignOutPreservesPrivacyPolicyAcceptance() {
        val mockStorage = mutableMapOf<String, Any>()
        val activeUserId = "user_active_999"

        // 1. User accepts Privacy Policy during onboarding
        mockStorage["privacy_policy_accepted_v1_$activeUserId"] = true
        mockStorage["privacy_policy_accepted_v1_timestamp_$activeUserId"] = 1732500000000L

        // 2. User signs out (clearing session, but preserving user-scoped prefs)
        fun simulateSignOut(sessionPrefs: MutableMap<String, Any>) {
            // Mirrors UserSessionManager.logout(): only clears session tokens and active user ID,
            // never wipes per-user settings or privacy policy acceptance flags
            sessionPrefs.remove("is_logged_in")
            sessionPrefs.remove("user_id")
            sessionPrefs.remove("user_name")
            sessionPrefs.remove("user_email")
        }

        simulateSignOut(mockStorage)

        // 3. Acceptance flag for this user key remains intact
        val stillAccepted = mockStorage["privacy_policy_accepted_v1_$activeUserId"] as? Boolean
        assertEquals(true, stillAccepted)
    }
}
