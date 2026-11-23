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
    // 1. Real-World Indian Bank SMS & UPI Alerts
    // ============================================================

    @Test
    fun testStandardIndianBankSmsFormats() {
        // Rs. 500.00 debited from A/c XX1234
        val res1 = TransactionNotificationParser.parse(
            packageName = "com.google.android.apps.messaging",
            title = "Bank Alert",
            text = "Rs. 500.00 debited from A/c XX1234 on 23-Nov-26. Info: Swiggy. Avl Bal: Rs 12,000.00"
        )
        assertNotNull(res1)
        assertEquals("Expense", res1!!.type)
        assertEquals(500.0, res1.amount, 0.01)
        assertEquals("Food", res1.category)

        // INR 1,250.00 credited to A/c
        val res2 = TransactionNotificationParser.parse(
            packageName = "com.google.android.apps.messaging",
            title = "Bank Alert",
            text = "INR 1,250.00 credited to A/c XX4321 by transfer from Rahul. Avl bal: INR 15,000.00"
        )
        assertNotNull(res2)
        assertEquals("Income", res2!!.type)
        assertEquals(1250.0, res2.amount, 0.01)

        // Sent Rs.250 to Merchant via UPI
        val res3 = TransactionNotificationParser.parse(
            packageName = "com.google.android.apps.nbu.paisa.user",
            title = "Google Pay",
            text = "Sent Rs.250 to Chai Point via UPI"
        )
        assertNotNull(res3)
        assertEquals("Expense", res3!!.type)
        assertEquals(250.0, res3.amount, 0.01)
        assertEquals("Food", res3.category)
        assertTrue(res3.title.contains("Chai Point", ignoreCase = true))

        // Paid ₹180.00 to Zomato
        val res4 = TransactionNotificationParser.parse(
            packageName = "com.phonepe.app",
            title = "PhonePe",
            text = "Paid ₹180.00 to Zomato"
        )
        assertNotNull(res4)
        assertEquals("Expense", res4!!.type)
        assertEquals(180.0, res4.amount, 0.01)
        assertEquals("Food", res4.category)
        assertTrue(res4.title.contains("Zomato", ignoreCase = true))

        // Acct XX987 debited with INR 450.00
        val res5 = TransactionNotificationParser.parse(
            packageName = "com.android.mms",
            title = "Axis Bank",
            text = "Acct XX987 debited with INR 450.00 on 23-Nov-26 at McDonald. Avl Bal: INR 8,400.00"
        )
        assertNotNull(res5)
        assertEquals("Expense", res5!!.type)
        assertEquals(450.0, res5.amount, 0.01)
        assertEquals("Food", res5.category)

        // Received Rs 1000 from ...
        val res6 = TransactionNotificationParser.parse(
            packageName = "net.one97.paytm",
            title = "Paytm",
            text = "Received Rs 1000 from Priya via UPI"
        )
        assertNotNull(res6)
        assertEquals("Income", res6!!.type)
        assertEquals(1000.0, res6.amount, 0.01)

        // Debited by Rs 99
        val res7 = TransactionNotificationParser.parse(
            packageName = "com.google.android.apps.messaging",
            title = "SBI",
            text = "A/c *8901 is debited by Rs 99.00 on 23-Nov-26 at Netflix. Avl Bal: Rs 4,500.00"
        )
        assertNotNull(res7)
        assertEquals("Expense", res7!!.type)
        assertEquals(99.0, res7.amount, 0.01)
        assertEquals("Entertainment", res7.category)

        // Spent Rs. 1,499 on HDFC Card
        val res8 = TransactionNotificationParser.parse(
            packageName = "com.google.android.apps.messaging",
            title = "HDFC Bank",
            text = "Spent Rs. 1,499 on HDFC Card ending 1234 at Amazon Retail on 23-Nov-26."
        )
        assertNotNull(res8)
        assertEquals("Expense", res8!!.type)
        assertEquals(1499.0, res8.amount, 0.01)
        assertEquals("Shopping", res8.category)
        assertTrue(res8.title.contains("Amazon", ignoreCase = true))
    }

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
    fun testKotakBankDebitSms() {
        val result = TransactionNotificationParser.parse(
            packageName = "com.google.android.apps.messaging",
            title = "Kotak Bank",
            text = "Sent Rs. 650.00 from Kotak Bank A/c 9876 to Blinkit on 23-11-2026. Ref no: 8493021."
        )
        assertNotNull(result)
        assertEquals("Expense", result!!.type)
        assertEquals(650.0, result.amount, 0.01)
        assertEquals("Grocery", result.category)
    }

    @Test
    fun testNaviLimitedCreditSms() {
        val result = TransactionNotificationParser.parse(
            packageName = "com.google.android.apps.messaging",
            title = "ICICI Bank",
            text = "NAVI LIMITED Deposited IN YOUR ICICI BANK Account XX123 INR 5,000.00 on 23-Nov-26. Avl Bal: INR 25,000.00"
        )
        assertNotNull(result)
        assertEquals("Income", result!!.type)
        assertEquals(5000.0, result.amount, 0.01)
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
    // 2. OTP Disclaimer Tolerance (Must NOT reject real transaction)
    // ============================================================

    @Test
    fun testSmsWithBankOtpDisclaimerIsNotRejected() {
        val result = TransactionNotificationParser.parse(
            packageName = "com.google.android.apps.messaging",
            title = "SBI",
            text = "Dear Customer, Rs.500.00 has been debited from account **1234 on 23-11-2026. Info: Swiggy. Bank never asks for OTP or PIN. Never share with anyone."
        )
        assertNotNull(result)
        assertEquals("Expense", result!!.type)
        assertEquals(500.0, result.amount, 0.01)
        assertEquals("Food", result.category)
    }

    @Test
    fun testSmsWithReferenceAndUtrNumbers() {
        val result = TransactionNotificationParser.parse(
            packageName = "com.google.android.apps.messaging",
            title = "HDFC Bank",
            text = "INR 2,400.00 debited from A/c XX4567 on 23-Nov-26 to Zomato. Ref: UPI/234901823/UTR 987654. Do not share password."
        )
        assertNotNull(result)
        assertEquals("Expense", result!!.type)
        assertEquals(2400.0, result.amount, 0.01)
        assertEquals("Food", result.category)
    }

    // ============================================================
    // 3. Multi-Field Extraction (BigText, SubText, TextLines)
    // ============================================================

    @Test
    fun testExtractionFromBigTextAndTextLines() {
        // When text is short summary ("1 new message") and full detail is in textLines or bigText
        val result = TransactionNotificationParser.parse(
            packageName = "com.google.android.apps.messaging",
            title = "ICICI Bank",
            text = "1 new message",
            bigText = "Dear Customer, your ICICI Bank Acc XX567 is debited for INR 1,200.00 on 23-Nov-26. Info: Apollo Pharmacy.",
            textLines = "ICICI Bank: debited for INR 1,200.00 at Apollo Pharmacy"
        )
        assertNotNull(result)
        assertEquals("Expense", result!!.type)
        assertEquals(1200.0, result.amount, 0.01)
        assertEquals("Healthcare", result.category)
    }

    // ============================================================
    // 4. International Bank & Digital Wallet Alerts
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
    // 5. Balance Exclusion Tests (Avl Bal must NOT override amount)
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
    // 6. Noise, Scam, OTP, Declined & Balance-Only Rejections
    // ============================================================

    @Test
    fun testPureOtpMessageIsRejected() {
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

    @Test
    fun testBalanceOnlyAlertIsRejected() {
        val result = TransactionNotificationParser.parse(
            packageName = "com.google.android.apps.messaging",
            title = "SBI Bank",
            text = "Your available balance in A/c XX1234 is Rs. 15,000.00."
        )
        assertNull(result)
    }

    // ============================================================
    // 7. Deduplication & Contradictory Credit/Debit Suppression Tests
    // ============================================================

    @Test
    fun testIncomingP2pTransferPaidYouIsClassifiedAsIncomeAndCleansTitle() {
        // Notification 1: UPI app alert where body is "AJAZ Ahmad WANI paid you ₹101.00"
        val res1 = TransactionNotificationParser.parse(
            packageName = "com.phonepe.app",
            title = "PhonePe",
            text = "AJAZ Ahmad WANI paid you ₹101.00"
        )
        assertNotNull(res1)
        assertEquals("Income", res1!!.type)
        assertEquals(101.0, res1.amount, 0.01)
        assertTrue(res1.title.contains("AJAZ Ahmad WANI", ignoreCase = true))
        assertFalse(res1.title.contains("paid you", ignoreCase = true))

        // Notification 2: UPI app alert where title has redundant "Paid You 101.00"
        val res2 = TransactionNotificationParser.parse(
            packageName = "com.google.android.apps.nbu.paisa.user",
            title = "AJAZ Ahmad WANI Paid You 101.00",
            text = "AJAZ Ahmad WANI paid you 101.00"
        )
        assertNotNull(res2)
        assertEquals("Income", res2!!.type)
        assertEquals(101.0, res2.amount, 0.01)
        assertTrue(res2.title.contains("AJAZ Ahmad WANI", ignoreCase = true))
        assertFalse(res2.title.contains("paid you", ignoreCase = true))
    }

    @Test
    fun testIncomingP2pSentYouAndReceivedFromClassifiedAsIncome() {
        val res1 = TransactionNotificationParser.parse(
            packageName = "com.phonepe.app",
            title = "PhonePe",
            text = "AJAZ Ahmad WANI sent you Rs. 101.00"
        )
        assertNotNull(res1)
        assertEquals("Income", res1!!.type)
        assertEquals(101.0, res1.amount, 0.01)
        assertTrue(res1.title.contains("AJAZ Ahmad WANI", ignoreCase = true))

        val res2 = TransactionNotificationParser.parse(
            packageName = "com.google.android.apps.messaging",
            title = "Bank Alert",
            text = "Received Rs. 101.00 from AJAZ Ahmad WANI on 23-Nov-26. Avl Bal: Rs 5,000.00"
        )
        assertNotNull(res2)
        assertEquals("Income", res2!!.type)
        assertEquals(101.0, res2.amount, 0.01)
        assertTrue(res2.title.contains("AJAZ Ahmad WANI", ignoreCase = true))

        val res3 = TransactionNotificationParser.parse(
            packageName = "com.google.android.apps.messaging",
            title = "SBI",
            text = "Rs. 101.00 credited to your account XX1234 by transfer from AJAZ Ahmad WANI"
        )
        assertNotNull(res3)
        assertEquals("Income", res3!!.type)
        assertEquals(101.0, res3.amount, 0.01)
        assertTrue(res3.title.contains("AJAZ Ahmad WANI", ignoreCase = true))
    }

    @Test
    fun testOutgoingDebitSentToAndPaidToClassifiedAsExpense() {
        val res1 = TransactionNotificationParser.parse(
            packageName = "com.phonepe.app",
            title = "PhonePe",
            text = "Paid Rs. 101.00 to AJAZ Ahmad WANI"
        )
        assertNotNull(res1)
        assertEquals("Expense", res1!!.type)
        assertEquals(101.0, res1.amount, 0.01)
        assertTrue(res1.title.contains("AJAZ Ahmad WANI", ignoreCase = true))

        val res2 = TransactionNotificationParser.parse(
            packageName = "com.google.android.apps.nbu.paisa.user",
            title = "Google Pay",
            text = "You paid Rs. 101.00 to AJAZ Ahmad WANI"
        )
        assertNotNull(res2)
        assertEquals("Expense", res2!!.type)
        assertEquals(101.0, res2.amount, 0.01)

        val res3 = TransactionNotificationParser.parse(
            packageName = "com.phonepe.app",
            title = "PhonePe",
            text = "Sent Rs. 101.00 to AJAZ Ahmad WANI"
        )
        assertNotNull(res3)
        assertEquals("Expense", res3!!.type)
        assertEquals(101.0, res3.amount, 0.01)

        val res4 = TransactionNotificationParser.parse(
            packageName = "com.google.android.apps.messaging",
            title = "HDFC Bank",
            text = "Rs. 101.00 debited from A/c XX1234 to AJAZ Ahmad WANI on 23-Nov-26"
        )
        assertNotNull(res4)
        assertEquals("Expense", res4!!.type)
        assertEquals(101.0, res4.amount, 0.01)
    }

    @Test
    fun testContradictoryCreditAndDebitDeduplicationWithinWindow() {
        val now = 1000000000L

        // 1. First event: Bank SMS informs Income ₹101 from AJAZ Ahmad WANI
        val isFirstDuplicate = TransactionDeduplicator.isDuplicate(101.0, "Income", "AJAZ Ahmad WANI", now)
        assertFalse(isFirstDuplicate)

        // 2. Second event: Near-simultaneous push alert arrives 2s later
        val isDuplicateIncome = TransactionDeduplicator.isDuplicate(101.0, "Income", "AJAZ Ahmad WANI", now + 2_000L)
        assertTrue(isDuplicateIncome)

        // 3. Third event: Contradictory entry (Expense ₹101 for same person within 25s) is rejected
        val isContradictoryExpense = TransactionDeduplicator.isDuplicate(101.0, "Expense", "AJAZ Ahmad WANI", now + 4_000L)
        assertTrue(isContradictoryExpense)

        // 4. Different merchant for the same amount ₹101 is NOT duplicate
        val isDifferentMerchantDuplicate = TransactionDeduplicator.isDuplicate(101.0, "Expense", "Zomato", now + 6_000L)
        assertFalse(isDifferentMerchantDuplicate)

        // 5. Same AJAZ Ahmad WANI after the 25-second window (> 25_000ms) is allowed
        val isAfterWindowDuplicate = TransactionDeduplicator.isDuplicate(101.0, "Income", "AJAZ Ahmad WANI", now + 26_000L)
        assertFalse(isAfterWindowDuplicate)
    }

    @Test
    fun testTightDeduplicationEngine() {
        val now = 1000000000L

        // First notification: Bank SMS for Rs 250 at Swiggy
        val isFirstDuplicate = TransactionDeduplicator.isDuplicate(250.0, "Expense", "Swiggy", now)
        assertFalse(isFirstDuplicate)

        // Second notification: GPay push alert 3 seconds later for same Rs 250 at Swiggy -> duplicate!
        val isSecondDuplicate = TransactionDeduplicator.isDuplicate(250.0, "Expense", "Swiggy", now + 3_000L)
        assertTrue(isSecondDuplicate)

        // Third notification: Same Rs 250, but at Chai Point 5 seconds later -> NOT a duplicate!
        val isDifferentMerchantDuplicate = TransactionDeduplicator.isDuplicate(250.0, "Expense", "Chai Point", now + 5_000L)
        assertFalse(isDifferentMerchantDuplicate)

        // Fourth notification: Same Swiggy Rs 250, but 30 seconds later (> 25s window) -> NOT a duplicate!
        val isAfterWindowDuplicate = TransactionDeduplicator.isDuplicate(250.0, "Expense", "Swiggy", now + 30_000L)
        assertFalse(isAfterWindowDuplicate)
    }
}
