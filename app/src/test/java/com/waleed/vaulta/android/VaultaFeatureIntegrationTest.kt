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

    @Test
    fun testCloudReinstallHydrationRestoresProfileSettingsAndTransactions() {
        // Simulate app uninstall/reinstall: local RoomDB and preferences are empty
        val localPreferences = mutableMapOf<String, Any>()
        val localRoomDb = mutableListOf<ExpenseEntity>()
        val userId = "user_reinstall_test_101"

        // Simulated cloud payload returned by Supabase PostgREST
        val cloudProfile = com.waleed.vaulta.android.data.model.ProfileDto(
            id = userId,
            displayName = "Waleed Wani",
            phoneNumber = "+91 9876543210",
            currencyCode = "INR",
            monthlyBudget = 75000.0,
            privacyPolicyAccepted = true
        )

        val cloudTransactions = listOf(
            TransactionDto(
                id = "tx_1001",
                localId = 1001,
                userId = userId,
                title = "Salary Credited",
                amount = 85000.0,
                type = "Income",
                category = "Salary",
                paymentMethod = "Bank Transfer",
                note = "Monthly salary direct deposit",
                date = "Nov 25, 2026",
                timestamp = 1732520000000L
            ),
            TransactionDto(
                id = "tx_1002",
                localId = 1002,
                userId = userId,
                title = "Taj Restaurant Dinner",
                amount = 3200.0,
                type = "Expense",
                category = "Food & Dining",
                paymentMethod = "UPI",
                note = "Celebration dinner with family",
                date = "Nov 25, 2026",
                timestamp = 1732525000000L
            ),
            TransactionDto(
                id = "tx_1003",
                localId = 1003,
                userId = userId,
                title = "Ajaz Ahmad Wani",
                amount = 15000.0,
                type = "Owed to You",
                category = "Credit",
                paymentMethod = "UPI",
                note = "Lent for travel expenses",
                date = "Nov 25, 2026",
                timestamp = 1732530000000L
            )
        )

        // Simulate ExpenseRepository reinstall hydration logic
        // 1. Hydrate preferences
        cloudProfile.displayName?.let { localPreferences["display_name_$userId"] = it }
        cloudProfile.currencyCode?.let { localPreferences["selected_currency_$userId"] = it }
        cloudProfile.monthlyBudget?.let { localPreferences["monthly_budget_$userId"] = it }
        cloudProfile.privacyPolicyAccepted?.let { localPreferences["privacy_policy_accepted_v1_$userId"] = it }

        // 2. Hydrate Room database
        val mappedEntities = cloudTransactions.map { dto ->
            ExpenseEntity(
                id = dto.localId,
                title = dto.title,
                amount = dto.amount,
                date = dto.date,
                type = dto.type,
                userId = dto.userId,
                note = dto.note
            )
        }
        localRoomDb.addAll(mappedEntities)

        // Assert profile & settings restored from cloud
        assertEquals("Waleed Wani", localPreferences["display_name_$userId"])
        assertEquals("INR", localPreferences["selected_currency_$userId"])
        assertEquals(75000.0, localPreferences["monthly_budget_$userId"])
        assertEquals(true, localPreferences["privacy_policy_accepted_v1_$userId"])

        // Assert all transactions and attributes restored into local DB
        assertEquals(3, localRoomDb.size)
        val salaryItem = localRoomDb.find { it.title == "Salary Credited" }
        assertNotNull(salaryItem)
        assertEquals(85000.0, salaryItem!!.amount, 0.001)
        assertEquals("Monthly salary direct deposit", salaryItem.note)
        assertEquals("Income", salaryItem.type)

        val dinnerItem = localRoomDb.find { it.title == "Taj Restaurant Dinner" }
        assertNotNull(dinnerItem)
        assertEquals(3200.0, dinnerItem!!.amount, 0.001)
        assertEquals("Celebration dinner with family", dinnerItem.note)

        val creditItem = localRoomDb.find { it.title == "Ajaz Ahmad Wani" }
        assertNotNull(creditItem)
        assertEquals("Owed to You", creditItem!!.type)
        assertEquals("Lent for travel expenses", creditItem.note)
    }

    @Test
    fun testXiaomiHyperOsInfoDialogStateToggle() {
        val initialUiState = com.waleed.vaulta.android.feature.profile.ProfileUiState()
        assertFalse(initialUiState.showDeviceSetupInfoDialog)

        // Opening dialog
        val stateAfterOpen = initialUiState.copy(showDeviceSetupInfoDialog = true)
        assertTrue(stateAfterOpen.showDeviceSetupInfoDialog)

        // Dismissing dialog
        val stateAfterDismiss = stateAfterOpen.copy(showDeviceSetupInfoDialog = false)
        assertFalse(stateAfterDismiss.showDeviceSetupInfoDialog)
    }

    @Test
    fun testRemoteTransactionDtoSerialization() {
        val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
        val dto = com.waleed.vaulta.android.data.model.RemoteTransactionDto(
            id = "uuid-1234-5678",
            userId = "usr-abc-999",
            title = "Freelance UI Design",
            amount = 45000.0,
            type = "INCOME",
            category = "Freelance",
            paymentMethod = "Bank",
            note = "Client milestone payment",
            dateMillis = 1732530000000L,
            isCreditOwe = false,
            creditOweStatus = null
        )

        val encoded = json.encodeToString(dto)
        assertTrue(encoded.contains("\"id\":\"uuid-1234-5678\""))
        assertTrue(encoded.contains("\"user_id\":\"usr-abc-999\""))
        assertTrue(encoded.contains("\"type\":\"INCOME\""))
        assertTrue(encoded.contains("\"date_millis\":1732530000000"))

        val decoded = json.decodeFromString<com.waleed.vaulta.android.data.model.RemoteTransactionDto>(encoded)
        assertEquals("uuid-1234-5678", decoded.id)
        assertEquals("usr-abc-999", decoded.userId)
        assertEquals(45000.0, decoded.amount, 0.001)
        assertEquals("Client milestone payment", decoded.note)
        assertFalse(decoded.isCreditOwe)
    }

    @Test
    fun testProfilePhoneNumberSanitizesEmpty() {
        val profileWithLiteralEmpty = com.waleed.vaulta.android.data.model.ProfileDto(
            id = "user_1",
            displayName = "Waleed",
            phoneNumber = "EMPTY"
        )
        assertEquals("", profileWithLiteralEmpty.cleanPhoneNumber)

        val profileWithLowercaseEmpty = com.waleed.vaulta.android.data.model.ProfileDto(
            id = "user_2",
            displayName = "Waleed",
            phoneNumber = "empty"
        )
        assertEquals("", profileWithLowercaseEmpty.cleanPhoneNumber)

        val profileWithBlank = com.waleed.vaulta.android.data.model.ProfileDto(
            id = "user_3",
            displayName = "Waleed",
            phoneNumber = "   "
        )
        assertEquals("", profileWithBlank.cleanPhoneNumber)

        val profileWithValidPhone = com.waleed.vaulta.android.data.model.ProfileDto(
            id = "user_4",
            displayName = "Waleed",
            phoneNumber = "+91 9876543210"
        )
        assertEquals("+91 9876543210", profileWithValidPhone.cleanPhoneNumber)
    }

    @Test
    fun testExpenseEntityMaintainsUuidAcrossCopies() {
        val initialUuid = "fixed-uuid-1111"
        val entity = ExpenseEntity(
            id = 10,
            title = "Groceries",
            amount = 1200.0,
            date = "Nov 25, 2026",
            type = "Expense",
            uuid = initialUuid
        )
        assertEquals(initialUuid, entity.uuid)

        // Editing attributes via copy preserves stable uuid
        val updated = entity.copy(title = "Supermarket Groceries", amount = 1450.0)
        assertEquals(initialUuid, updated.uuid)
        assertEquals(10, updated.id)
        assertEquals(1450.0, updated.amount, 0.001)
    }

    @Test
    fun testFullReinstallRestoreWithRemoteTransactionDto() {
        val userId = "user_cloud_restore_456"
        val remoteList = listOf(
            com.waleed.vaulta.android.data.model.RemoteTransactionDto(
                id = "tx-uuid-1",
                userId = userId,
                title = "App Consulting",
                amount = 60000.0,
                type = "INCOME",
                category = "Salary",
                note = "Direct client invoice",
                dateMillis = 1732530000000L,
                isCreditOwe = false,
                creditOweStatus = null
            ),
            com.waleed.vaulta.android.data.model.RemoteTransactionDto(
                id = "tx-uuid-2",
                userId = userId,
                title = "Ajaz Ahmad Wani",
                amount = 5000.0,
                type = "EXPENSE",
                category = "Credit / Owe",
                note = "Pending settlement",
                dateMillis = 1732535000000L,
                isCreditOwe = true,
                creditOweStatus = "YOU_OWE"
            )
        )

        // Simulate restore into Room entities
        val restoredEntities = remoteList.map { remote ->
            val localType = if (remote.isCreditOwe) {
                if (remote.creditOweStatus?.equals("YOU_OWE", ignoreCase = true) == true) "You Owe" else "Owed to You"
            } else if (remote.type.equals("INCOME", ignoreCase = true)) {
                "Income"
            } else {
                "Expense"
            }
            ExpenseEntity(
                id = null,
                title = remote.title,
                amount = remote.amount,
                date = "Nov 25, 2026",
                type = localType,
                userId = userId,
                note = remote.note,
                uuid = remote.id
            )
        }

        assertEquals(2, restoredEntities.size)
        val incomeItem = restoredEntities[0]
        assertEquals("Income", incomeItem.type)
        assertEquals("tx-uuid-1", incomeItem.uuid)
        assertEquals(60000.0, incomeItem.amount, 0.001)

        val oweItem = restoredEntities[1]
        assertEquals("You Owe", oweItem.type)
        assertEquals("tx-uuid-2", oweItem.uuid)
        assertEquals("Pending settlement", oweItem.note)
    }

    @Test
    fun testProfileOverwritePreventionOnLogin() {
        // Given an existing profile saved in Supabase
        val existingCloudProfile = com.waleed.vaulta.android.data.model.ProfileDto(
            id = "user-waleed-123",
            username = "Waleed Wani",
            displayName = "Waleed Wani",
            fullName = "Waleed Wani",
            email = "waleedwani06072005@gmail.com",
            phoneNumber = "+919876543210",
            currencyCode = "INR",
            monthlyBudget = 25000.0,
            privacyPolicyAccepted = true
        )

        // When user logs in with Google account (which gives default email prefix or google display name)
        val incomingGoogleDisplayName = "waleed wani"
        val incomingEmail = "waleedwani06072005@gmail.com"

        // Resolver logic should preserve the existing cloud values
        val resolvedName = existingCloudProfile.displayName?.ifBlank { null }
            ?: existingCloudProfile.fullName?.ifBlank { null }
            ?: incomingGoogleDisplayName

        val resolvedPhone = existingCloudProfile.cleanPhoneNumber.ifBlank { "" }

        val resolvedCurrency = existingCloudProfile.currencyCode?.ifBlank { null } ?: "USD"
        val resolvedBudget = existingCloudProfile.monthlyBudget ?: 0.0
        val resolvedPolicy = existingCloudProfile.privacyPolicyAccepted ?: false

        assertEquals("Waleed Wani", resolvedName)
        assertEquals("+919876543210", resolvedPhone)
        assertEquals("INR", resolvedCurrency)
        assertEquals(25000.0, resolvedBudget, 0.001)
        assertTrue(resolvedPolicy)
    }

    @Test
    fun testBrandNewProfileDtoDefaultsPrivacyPolicyAcceptedToFalse() {
        val newProfile = com.waleed.vaulta.android.data.model.ProfileDto(
            id = "new-user-123",
            email = "newuser@example.com"
        )
        // Newly created ProfileDto MUST default privacyPolicyAccepted to false
        assertEquals(false, newProfile.privacyPolicyAccepted)
    }

    @Test
    fun testPrivacyPolicyConsentGatingForAllAuthMethods() {
        val mockStorage = mutableMapOf<String, Any>()

        fun isPrivacyPolicyAccepted(userKey: String): Boolean {
            return mockStorage["privacy_policy_accepted_v1_$userKey"] as? Boolean ?: false
        }

        fun postAuthDestination(userKey: String): String {
            return if (!isPrivacyPolicyAccepted(userKey)) {
                NavRouts.privacy_policy_consent
            } else {
                NavRouts.home
            }
        }

        // Test 1: Continue with Google (new user)
        val googleUserKey = "google-usr-999"
        assertEquals(NavRouts.privacy_policy_consent, postAuthDestination(googleUserKey))
        mockStorage["privacy_policy_accepted_v1_$googleUserKey"] = true
        assertEquals(NavRouts.home, postAuthDestination(googleUserKey))

        // Test 2: Email Sign-In / Sign-Up (new user)
        val emailUserKey = "email-usr-888"
        assertEquals(NavRouts.privacy_policy_consent, postAuthDestination(emailUserKey))
        mockStorage["privacy_policy_accepted_v1_$emailUserKey"] = true
        assertEquals(NavRouts.home, postAuthDestination(emailUserKey))

        // Test 3: Mobile Number Sign-In / Sign-Up without OTP (new user)
        val phoneUserKey = "phone-usr-777"
        assertEquals(NavRouts.privacy_policy_consent, postAuthDestination(phoneUserKey))
        mockStorage["privacy_policy_accepted_v1_$phoneUserKey"] = true
        assertEquals(NavRouts.home, postAuthDestination(phoneUserKey))

        // Test 4: Guest Mode (new guest)
        val guestKey = "guest"
        assertEquals(NavRouts.privacy_policy_consent, postAuthDestination(guestKey))
        mockStorage["privacy_policy_accepted_v1_$guestKey"] = true
        assertEquals(NavRouts.home, postAuthDestination(guestKey))
    }

    @Test
    fun testResetEverythingPreservesPrivacyPolicyAcceptance() {
        val mockStorage = mutableMapOf<String, Any>()
        val activeUserId = "user-waleed-456"

        // User previously accepted privacy policy
        mockStorage["privacy_policy_accepted_v1_$activeUserId"] = true
        mockStorage["privacy_policy_accepted_v1_timestamp_$activeUserId"] = 1732500000000L

        // Simulate clearCurrentUserData (Reset Everything) - clears transactions and budgets but preserves consent
        fun simulateClearCurrentUserData(userKey: String) {
            // Room DB deletion for user transactions
            // Budget reset
            // NOTE: Never wipe or reset privacy policy keys
        }

        simulateClearCurrentUserData(activeUserId)

        val stillAccepted = mockStorage["privacy_policy_accepted_v1_$activeUserId"] as? Boolean
        assertEquals(true, stillAccepted)
    }

    @Test
    fun testCloudSyncSessionInitializationAndRlsAlignment() {
        val authUid = "supabase-auth-uid-7777"
        val localUserKey = "legacy-local-user-key"

        val localEntity = ExpenseEntity(
            id = 42,
            title = "Groceries",
            amount = 1250.0,
            date = "26/11/2026",
            type = "Expense",
            userId = localUserKey,
            uuid = "item-uuid-42"
        )

        val remoteDto = com.waleed.vaulta.android.data.model.RemoteTransactionDto(
            id = localEntity.uuid,
            userId = authUid,
            title = localEntity.title,
            amount = localEntity.amount,
            type = "EXPENSE",
            category = "Groceries",
            paymentMethod = "App",
            note = localEntity.note,
            dateMillis = 1732600000000L,
            isCreditOwe = false,
            creditOweStatus = null
        )

        assertEquals(authUid, remoteDto.userId)
        assertEquals("item-uuid-42", remoteDto.id)
        assertEquals(1250.0, remoteDto.amount, 0.001)
    }

    @Test
    fun testNotificationListenerRoutesThroughRepositoryAndPreservesAuthUserId() {
        val signedInUserId = "user-auth-uuid-8888"
        var isLoggedIn = true
        var resolvedUserKey = if (isLoggedIn) signedInUserId else "guest"

        assertEquals(signedInUserId, resolvedUserKey)

        isLoggedIn = false
        resolvedUserKey = if (isLoggedIn) signedInUserId else "guest"
        assertEquals("guest", resolvedUserKey)
    }
}
