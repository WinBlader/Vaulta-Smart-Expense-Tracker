package com.waleed.vaulta.android.data.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TransactionNotificationParserTest {

    @Before
    fun setUp() {
        TransactionDeduplicator.clear()
    }

    // ============================================================
    // 1. Indian Bank SMS & UPI Alerts
    // ============================================================

    @Test
    fun testHdfcBankDebitSms() {
        val result = TransactionNotificationParser.parse(
            packageName = "com.google.android.apps.messaging",
            title = "HDFC Bank",
            text = "HDFC Bank: Rs 1,450.00 debited from a/c **1234 on 22-11-26 at SWIGGY. Avl bal: Rs 25,600.50"
        )
        assertNotNull(result)
        assertEquals("Expense", result!!.type)
        assertEquals(1450.0, result.amount, 0.01)
        assertEquals("Food", result.category)
        assertTrue(result.title.contains("Swiggy", ignoreCase = true))
    }

    @Test
    fun testIciciBankDebitSms() {
        val result = TransactionNotificationParser.parse(
            packageName = "com.google.android.apps.messaging",
            title = "ICICI Bank",
            text = "Dear Customer, your ICICI Bank Acc XX567 is debited for INR 2,999.00 on 22-Nov-26. Info: Amazon Retail. Avl Bal: INR 15,200.00"
        )
        assertNotNull(result)
        assertEquals("Expense", result!!.type)
        assertEquals(2999.0, result.amount, 0.01)
        assertEquals("Shopping", result.category)
        assertTrue(result.title.contains("Amazon", ignoreCase = true))
    }

    @Test
    fun testSbiBankDebitSms() {
        val result = TransactionNotificationParser.parse(
            packageName = "com.android.mms",
            title = "SBI Bank",
            text = "Dear SBI User, A/c *8901 debited by Rs. 350.00 on 22Nov26 transfer to Uber. Avl Bal Rs 4,500.00"
        )
        assertNotNull(result)
        assertEquals("Expense", result!!.type)
        assertEquals(350.0, result.amount, 0.01)
        assertEquals("Transport", result.category)
        assertTrue(result.title.contains("Uber", ignoreCase = true))
    }

    @Test
    fun testJnKBankCreditSalarySms() {
        val result = TransactionNotificationParser.parse(
            packageName = "com.samsung.android.messaging",
            title = "J&K Bank",
            text = "J&K Bank: Rs 50,000.00 credited to your A/c XX4321 on 22-11-2026 by Salary. Avl Bal Rs 52,100.00"
        )
        assertNotNull(result)
        assertEquals("Income", result!!.type)
        assertEquals(50000.0, result.amount, 0.01)
        assertEquals("Salary", result.category)
    }

    @Test
    fun testGooglePayPushNotification() {
        val result = TransactionNotificationParser.parse(
            packageName = "com.google.android.apps.nbu.paisa.user",
            title = "Google Pay",
            text = "Paid ₹250.00 to Swiggy"
        )
        assertNotNull(result)
        assertEquals("Expense", result!!.type)
        assertEquals(250.0, result.amount, 0.01)
        assertEquals("Food", result.category)
        assertTrue(result.title.contains("Swiggy", ignoreCase = true))
    }

    @Test
    fun testPhonePePushNotification() {
        val result = TransactionNotificationParser.parse(
            packageName = "com.phonepe.app",
            title = "PhonePe",
            text = "Payment of ₹180 to Chai Point successful"
        )
        assertNotNull(result)
        assertEquals("Expense", result!!.type)
        assertEquals(180.0, result.amount, 0.01)
        assertEquals("Food", result.category)
        assertTrue(result.title.contains("Chai Point", ignoreCase = true))
    }

    // ============================================================
    // 2. International Bank & Digital Wallet Alerts
    // ============================================================

    @Test
    fun testChaseUsdAlert() {
        val result = TransactionNotificationParser.parse(
            packageName = "com.chase.sig.android",
            title = "Chase Alert",
            text = "Chase: You made a $42.50 purchase at STARBUCKS with card ending 1234."
        )
        assertNotNull(result)
        assertEquals("Expense", result!!.type)
        assertEquals(42.50, result.amount, 0.01)
        assertEquals("USD", result.currencyCode)
        assertEquals("Food", result.category)
        assertTrue(result.title.contains("Starbucks", ignoreCase = true))
    }

    @Test
    fun testRevolutEurAlertWithEuropeanComma() {
        val result = TransactionNotificationParser.parse(
            packageName = "com.revolut.revolut",
            title = "Revolut",
            text = "You spent €19,99 at Spotify."
        )
        assertNotNull(result)
        assertEquals("Expense", result!!.type)
        assertEquals(19.99, result.amount, 0.01)
        assertEquals("EUR", result.currencyCode)
        assertEquals("Entertainment", result.category)
        assertTrue(result.title.contains("Spotify", ignoreCase = true))
    }

    @Test
    fun testMonzoGbpAlert() {
        val result = TransactionNotificationParser.parse(
            packageName = "com.monzo.android",
            title = "Monzo",
            text = "£85.00 spent at Tesco."
        )
        assertNotNull(result)
        assertEquals("Expense", result!!.type)
        assertEquals(85.0, result.amount, 0.01)
        assertEquals("GBP", result.currencyCode)
        assertEquals("Grocery", result.category)
        assertTrue(result.title.contains("Tesco", ignoreCase = true))
    }

    @Test
    fun testWiseAedTransferAlert() {
        val result = TransactionNotificationParser.parse(
            packageName = "com.transferwise.android",
            title = "Wise",
            text = "You sent 150.75 AED to Ahmed."
        )
        assertNotNull(result)
        assertEquals("Expense", result!!.type)
        assertEquals(150.75, result.amount, 0.01)
        assertEquals("AED", result.currencyCode)
        assertTrue(result.title.contains("Ahmed", ignoreCase = true))
    }

    @Test
    fun testPayPalIncomeAlert() {
        val result = TransactionNotificationParser.parse(
            packageName = "com.paypal.android.p2pmobile",
            title = "PayPal",
            text = "You received $120.00 USD from Client LLC."
        )
        assertNotNull(result)
        assertEquals("Income", result!!.type)
        assertEquals(120.0, result.amount, 0.01)
        assertEquals("USD", result.currencyCode)
        assertTrue(result.title.contains("Client LLC", ignoreCase = true))
    }

    @Test
    fun testGoogleWalletNfcAlert() {
        val result = TransactionNotificationParser.parse(
            packageName = "com.google.android.apps.walletnfcrel",
            title = "Google Wallet",
            text = "Paid USD 15.00 at Shell Gas Station."
        )
        assertNotNull(result)
        assertEquals("Expense", result!!.type)
        assertEquals(15.0, result.amount, 0.01)
        assertEquals("USD", result.currencyCode)
        assertEquals("Transport", result.category)
        assertTrue(result.title.contains("Shell", ignoreCase = true))
    }

    // ============================================================
    // 3. Balance Exclusion Tests (Avl Bal must NOT override amount)
    // ============================================================

    @Test
    fun testBalanceExclusionDoesNotOverrideTransactionAmount() {
        val result = TransactionNotificationParser.parse(
            packageName = "com.google.android.apps.messaging",
            title = "Bank Alert",
            text = "Acct XX1234 debited by Rs 150.00. Avl Bal: Rs 45,000.00"
        )
        assertNotNull(result)
        assertEquals(150.0, result!!.amount, 0.01)
    }

    @Test
    fun testBalanceExclusionWithMultipleBalances() {
        val result = TransactionNotificationParser.parse(
            packageName = "com.google.android.apps.messaging",
            title = "HDFC Bank",
            text = "Dear Customer, INR 520.00 debited from A/c XX9900 on 22-11-26. Total Available Balance is INR 88,320.50."
        )
        assertNotNull(result)
        assertEquals(520.0, result!!.amount, 0.01)
    }

    // ============================================================
    // 4. Noise, Scam, OTP, Declined & Request Rejections
    // ============================================================

    @Test
    fun testOtpMessageIsRejected() {
        val result = TransactionNotificationParser.parse(
            packageName = "com.google.android.apps.messaging",
            title = "Bank OTP",
            text = "Your OTP for transaction of Rs 500 at Amazon is 492011. Do not share with anyone."
        )
        assertNull(result)
    }

    @Test
    fun testDeclinedTransactionIsRejected() {
        val result = TransactionNotificationParser.parse(
            packageName = "com.chase.sig.android",
            title = "Chase Alert",
            text = "Your transaction of $45.00 at Walmart was declined due to insufficient funds."
        )
        assertNull(result)
    }

    @Test
    fun testFailedPaymentIsRejected() {
        val result = TransactionNotificationParser.parse(
            packageName = "com.phonepe.app",
            title = "PhonePe",
            text = "Payment of ₹1,000 to John failed. Amount if deducted will be refunded."
        )
        assertNull(result)
    }

    @Test
    fun testPaymentRequestIsRejected() {
        val result = TransactionNotificationParser.parse(
            packageName = "com.venmo",
            title = "Venmo",
            text = "Jane Doe has requested $25.00 from you on Venmo."
        )
        assertNull(result)
    }

    @Test
    fun testAutopayMandateIsRejected() {
        val result = TransactionNotificationParser.parse(
            packageName = "com.google.android.apps.messaging",
            title = "Bank Mandate",
            text = "Autopay mandate created for Rs 499 on your A/c XX1234."
        )
        assertNull(result)
    }

    @Test
    fun testPromotionalMarketingIsRejected() {
        val result = TransactionNotificationParser.parse(
            packageName = "net.one97.paytm",
            title = "Paytm Offers",
            text = "Win up to ₹500 cashback on your next recharge! Apply now."
        )
        assertNull(result)
    }

    // ============================================================
    // 5. 2-Minute Deduplication Engine Tests
    // ============================================================

    @Test
    fun testDuplicateSuppressionWithinTwoMinutes() {
        val now = 1000000000L

        // First notification: Bank SMS for Rs 250
        val isFirstDuplicate = TransactionDeduplicator.isDuplicate(250.0, "Expense", now)
        assertFalse(isFirstDuplicate)

        // Second notification: GPay push alert 15 seconds later for the same Rs 250
        val isSecondDuplicate = TransactionDeduplicator.isDuplicate(250.0, "Expense", now + 15_000L)
        assertTrue(isSecondDuplicate)

        // Third notification: Different amount 30 seconds later -> should NOT be duplicate
        val isDifferentAmountDuplicate = TransactionDeduplicator.isDuplicate(400.0, "Expense", now + 30_000L)
        assertFalse(isDifferentAmountDuplicate)

        // Fourth notification: Same Rs 250 amount, but 3 minutes later -> should NOT be duplicate
        val isAfterTwoMinutesDuplicate = TransactionDeduplicator.isDuplicate(250.0, "Expense", now + 180_000L)
        assertFalse(isAfterTwoMinutesDuplicate)
    }
}
