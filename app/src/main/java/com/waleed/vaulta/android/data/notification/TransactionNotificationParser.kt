package com.waleed.vaulta.android.data.notification

import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class ParsedTransaction(
    val title: String,
    val amount: Double,
    val type: String, // "Income" or "Expense"
    val category: String,
    val currencyCode: String?,
    val originalCurrencySymbol: String?,
    val rawMerchant: String?,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Thread-safe deduplication engine to suppress duplicate and contradictory transaction logs
 * (e.g. when Bank SMS and UPI/Wallet push alert fire simultaneously for the exact same transaction).
 * Uses a 25-second window keyed on amount and merchant/sender name to prevent:
 * 1. Duplicate entries (same amount + matching sender within 25s)
 * 2. Contradictory credit + debit entries (e.g. Income ₹101 and Expense ₹101 for the same event)
 */
object TransactionDeduplicator {
    private const val DEDUP_WINDOW_MILLIS = 25_000L // 25 seconds

    private data class RecordedTx(
        val amount: Double,
        val type: String,
        val merchant: String,
        val timestamp: Long
    )

    private val recentTransactions = ConcurrentHashMap<String, RecordedTx>()

    @Synchronized
    fun isDuplicate(
        amount: Double,
        type: String,
        merchant: String = "",
        currentTime: Long = System.currentTimeMillis()
    ): Boolean {
        // Purge expired records older than the deduplication window
        val iterator = recentTransactions.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (currentTime - entry.value.timestamp > DEDUP_WINDOW_MILLIS) {
                iterator.remove()
            }
        }

        val normMerchant = merchant.trim().lowercase(Locale.US)
            .replace(Regex("""\s+"""), " ")

        // Check against active recent transactions
        for (recorded in recentTransactions.values) {
            val isSameAmount = kotlin.math.abs(recorded.amount - amount) < 0.009
            if (isSameAmount && (currentTime - recorded.timestamp) <= DEDUP_WINDOW_MILLIS) {
                val recordedMerchant = recorded.merchant
                val merchantsMatch = normMerchant.isBlank() || recordedMerchant.isBlank() ||
                        normMerchant == recordedMerchant ||
                        normMerchant.contains(recordedMerchant) ||
                        recordedMerchant.contains(normMerchant)

                if (merchantsMatch) {
                    // Scenario 1: Same type and amount and merchant -> Duplicate alert (e.g. SMS + Push)
                    if (recorded.type.equals(type, ignoreCase = true)) {
                        return true
                    }
                    // Scenario 2: Contradictory type (one Income, one Expense) for the same amount and merchant within 25s
                    // Never allow an incoming credit and outgoing debit for the same party/amount simultaneously!
                    return true
                }
            }
        }

        val entryKey = "${type.uppercase(Locale.US)}_${String.format(Locale.US, "%.2f", amount)}_$normMerchant"
        recentTransactions[entryKey] = RecordedTx(
            amount = amount,
            type = type,
            merchant = normMerchant,
            timestamp = currentTime
        )
        return false
    }

    @Synchronized
    fun clear() {
        recentTransactions.clear()
    }
}

/**
 * High-performance, pure-Kotlin, privacy-preserving notification parser for
 * Indian UPI & SMS alerts and global multi-currency payment notifications.
 */
object TransactionNotificationParser {

    private const val SELF_PACKAGE = "com.waleed.vaulta.android"

    // Standard SMS / MMS messaging packages across Google, OEM vendors, and trusted SMS organizers
    val ALLOWED_SMS_PACKAGES = setOf(
        "com.google.android.apps.messaging",  // Google Messages
        "com.android.mms",                    // Standard Android / AOSP MMS
        "com.samsung.android.messaging",      // Samsung Messages
        "com.miui.mms",                       // Xiaomi / MIUI / HyperOS Messaging
        "com.android.mms.service",            // MMS Service
        "com.oneplus.mms",                    // OnePlus SMS
        "com.oppo.mms",                       // Oppo SMS
        "com.coloros.mms",                    // ColorOS / Realme SMS
        "com.vivo.message",                   // Vivo SMS
        "com.microsoft.android.smsorganizer", // Microsoft SMS Organizer
        "com.motorola.messaging",             // Motorola Messages
        "com.huawei.message",                 // Huawei Messages
        "com.huawei.mms",                     // Huawei MMS
        "com.asus.message",                   // Asus Messages
        "com.sonyericsson.conversations"      // Sony Xperia Messages
    )

    // Explicitly blocked social, instant messaging, and chat apps
    val BLOCKED_PACKAGES = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
        "org.telegram.messenger",
        "org.telegram.plus",
        "org.thunderdog.challegram",
        "com.instagram.android",
        "com.discord",
        "com.snapchat.android",
        "com.facebook.orca",
        "com.facebook.katana",
        "com.facebook.lite",
        "com.linkedin.android",
        "com.slack",
        "com.twitter.android",
        "com.x.android",
        "com.viber.voip",
        "com.skype.raider",
        "com.tencent.mm",
        "jp.naver.line.android",
        "org.thoughtcrime.securesms",
        "com.reddit.frontpage",
        "com.google.android.gm",
        "com.microsoft.office.outlook"
    )

    /**
     * Checks whether the incoming notification package belongs to an authorized SMS messaging app
     * or the device's currently configured default SMS application.
     */
    fun isSmsPackage(packageName: String, defaultSmsPackage: String? = null): Boolean {
        if (packageName.isBlank()) return false
        if (packageName == SELF_PACKAGE) return false
        if (BLOCKED_PACKAGES.contains(packageName)) return false

        // 1. Check if matches dynamically detected system default SMS package
        if (!defaultSmsPackage.isNullOrBlank() && packageName.equals(defaultSmsPackage, ignoreCase = true)) {
            return true
        }

        // 2. Check recognized standard SMS allowlist
        return ALLOWED_SMS_PACKAGES.contains(packageName)
    }

    // Strict Noise & Scam Filter:
    // Notice: Disclaimers like "Bank never asks for OTP" or "Do not share OTP" are NOT treated as OTP prompts!
    // Only real OTP/auth challenges or failed/declined transactions are filtered.
    private val OTP_PROMPT_REGEX = Regex(
        """(?i)\b(?:is\s+your\s+otp|otp\s+is\s*:?\s*\d+|\byour\s+otp\s+(?:for|is)\b|\buse\s+(?:the\s+)?otp\s+\d+|\bone[- ]time\s+password\s+is\b|\bverification\s+code\s+is\s*:?\s*\d+|\buse\s+code\s+\d+\s+to\b|\blogin\s+otp\b|\botp\s+to\s+verify\b|\benter\s+otp\b|\bsecret\s+code\s+is\b)"""
    )

    private val FAILED_TRANSACTION_REGEX = Regex(
        """(?i)\b(?:(?:transaction|payment|transfer|order)\s+(?:has\s+)?(?:failed|declined|cancelled|canceled|rejected)|(?:failed|declined|unsuccessful|rejected)\s+(?:to\s+debit|to\s+credit|transaction|payment|transfer)|insufficient\s+(?:funds|balance)|payment\s+(?:of\s+.*?\s+)?(?:failed|declined|unsuccessful)|(?:transaction|payment)\s+reversed\s+due\s+to\s+failure)\b"""
    )

    private val REQUEST_OR_MANDATE_REGEX = Regex(
        """(?i)\b(?:has\s+requested|requested\s+(?:money|payment|[₹$€£]|rs\.?|inr|usd)|collect\s+request|payment\s+request|autopay\s+mandate\s+created|mandate\s+registered|request\s+received)\b"""
    )

    private val PROMO_REGEX = Regex(
        """(?i)\b(?:win\s+up\s+to|flat\s+\d+%\s+off|pre[- ]approved\s+loan|apply\s+now|claim\s+your|special\s+offer|congratulations\s+you\s+won|credit\s+score|cibil\s+score|credit\s+report|instant\s+loan)\b"""
    )

    // Financial & banking context verification
    // Ensures casual chat messages or random text messages mentioning numbers/currencies
    // without genuine banking/account context are strictly ignored.
    private val FINANCIAL_CONTEXT_REGEX = Regex(
        """(?i)\b(?:a/c|acct|account|card\b|ending\s+(?:in\s+)?(?:\d+|x+)|upi|vpa|utr|ref(?:\s*no\.?)?|txn|trans|bank|neft|rtgs|imps|pos\b|atm\b|bal(?:ance)?|avl\.?\s*bal|debited\s+(?:from|by|with|for)|is\s+debited|debited\b|credited\s+(?:to|with|by|for)|is\s+credited|credited\b|paid\s+(?:to|at|you)|has\s+paid|sent\s+(?:to|you)|has\s+sent|transferred\s+(?:to|from)|has\s+transferred|deposited\s+(?:in|into)|withdrawn|payment\s+(?:of|to|from)|received\s+(?:from|via)|money\s+received)\b"""
    )

    // Income indicators (money coming IN to the user)
    // Phrases like "paid you", "sent you", "transferred to you" clearly signify the user is the recipient!
    private val INCOME_REGEX = Regex(
        """(?i)\b(paid\s+you|has\s+paid\s+you|sent\s+you|has\s+sent\s+you|transferred\s+to\s+you|paid\s+to\s+you|payment\s+from|received\s+from|credited\s+to|credited\s+with|credited\s+by|credited\s+for|deposited\s+in|deposited\s+into|credited|credit|received|deposited|deposit|refund|refunded|cashback|salary|payout|transfer\s+from|transferred\s+from|you\s+received|money\s+received)\b"""
    )

    // Expense indicators (money going OUT from the user)
    // Note: Do NOT match standalone "paid" or "sent" if immediately followed by "you" (which means incoming money)!
    private val EXPENSE_REGEX = Regex(
        """(?i)\b(you\s+paid|paid\s+to|paid\s+at|payment\s+to|payment\s+of|you\s+sent|sent\s+to|transfer\s+to|transferred\s+to|debited\s+from|debited\s+by|debited\s+with|debited\s+for|debited|debit|spent|withdrawn|withdrawal|purchased|purchase|charged|charge|pos\s+txn|atm|paid(?!\s+you\b)|sent(?!\s+you\b))\b"""
    )

    // Balances to strip before amount search so available balances are not mistaken for transaction amounts
    private val BALANCE_REGEX = Regex(
        """(?i)\b(?:(?:total|available|avl\.?)\s+)*(?:bal(?:ance)?|a/c\s+bal)\s*(?:is|:|=|-|\s)\s*(?:([₹$€£]|Rs\.?|INR|USD|EUR|GBP|AED|SAR|SGD|AUD|CAD|JPY)\s*([\d,.]+)|([\d,.]+)\s*([₹$€£]|Rs\.?|INR|USD|EUR|GBP|AED|SAR|SGD|AUD|CAD|JPY))""",
        RegexOption.IGNORE_CASE
    )

    private val BALANCE_SIMPLE_REGEX = Regex(
        """(?i)\b(?:avl\.?\s*bal(?:ance)?|bal(?:ance)?)\s*[:=]\s*(?:[₹$€£]|Rs\.?|INR|USD|EUR|GBP|AED|SAR|SGD|AUD|CAD|JPY)?\s*[\d,.]+""",
        RegexOption.IGNORE_CASE
    )

    // Account & card references to strip
    private val ACCOUNT_CARD_REGEX = Regex(
        """(?i)\b(?:a/c(?:\s*no\.?)?|acct(?:\s*no\.?)?|account(?:\s*no\.?)?|card(?:\s*ending(?:\s*in)?)?)\s*(?:[*xX\d]+)""",
        RegexOption.IGNORE_CASE
    )

    // Currency & Amount extraction pattern
    // Supported currencies: ₹, Rs., Rs, INR, $, USD, €, EUR, £, GBP, AED, SAR, SGD, AUD, CAD, JPY
    // Also matches numbers directly preceded by transaction verbs like "paid you 101.00", "sent you 101.00", or "sent 150.75 AED"
    private val AMOUNT_PATTERN = Regex(
        """(?i)(?:([₹$€£]|Rs\.?|INR|USD|EUR|GBP|AED|SAR|SGD|AUD|CAD|JPY)\s*(\d[\d,.]*)|\b(\d[\d,.]*)\s*([₹$€£]|Rs\.?|INR|USD|EUR|GBP|AED|SAR|SGD|AUD|CAD|JPY)|\b(?:paid|sent|received|credited|debited)\s+(?:you\s+)?(?:of\s+)?(?:for\s+)?(?:by\s+)?([₹$€£]|Rs\.?|INR|USD|EUR|GBP|AED|SAR|SGD|AUD|CAD|JPY)?\s*(\d[\d,.]*)\s*([₹$€£]|Rs\.?|INR|USD|EUR|GBP|AED|SAR|SGD|AUD|CAD|JPY)?)"""
    )

    /**
     * Parses a notification from its package, title, and body text fields.
     * Enforces strict SMS-only package filtering and financial context validation.
     * Returns a valid [ParsedTransaction] or null if the notification is not a completed financial transaction.
     */
    fun parse(
        packageName: String,
        title: String,
        text: String,
        bigText: String? = null,
        subText: String? = null,
        textLines: String? = null,
        postTime: Long = System.currentTimeMillis(),
        defaultSmsPackage: String? = null
    ): ParsedTransaction? {
        // Enforce SMS-only package filtering (reject chat, social, or unauthorized apps)
        if (!isSmsPackage(packageName, defaultSmsPackage)) {
            return null
        }

        // Collect all available text sources
        val bodyParts = listOfNotNull(bigText, textLines, text, subText)
            .filter { it.isNotBlank() }
            .distinct()
        val fullBody = if (bodyParts.isNotEmpty()) bodyParts.joinToString(" ") else text
        val combinedText = "$title $fullBody".trim()

        if (combinedText.isBlank()) return null

        // 1. Noise, scam, OTP, failure, promo, and financial context filtering
        if (OTP_PROMPT_REGEX.containsMatchIn(combinedText)) {
            return null
        }
        if (FAILED_TRANSACTION_REGEX.containsMatchIn(combinedText)) {
            return null
        }
        if (REQUEST_OR_MANDATE_REGEX.containsMatchIn(combinedText)) {
            return null
        }
        if (PROMO_REGEX.containsMatchIn(combinedText)) {
            return null
        }
        if (!FINANCIAL_CONTEXT_REGEX.containsMatchIn(combinedText)) {
            return null
        }

        // 2. Classify transaction type (Income vs Expense)
        val hasIncome = INCOME_REGEX.containsMatchIn(combinedText)
        val hasExpense = EXPENSE_REGEX.containsMatchIn(combinedText)

        if (!hasIncome && !hasExpense) {
            return null
        }

        // Priority resolution:
        // Phrases like "paid you", "sent you", "transferred to you", "payment from" are 100% incoming income.
        val hasExplicitPaidYou = Regex("""(?i)\b(?:has\s+)?(?:paid|sent|transferred)\s+you\b""").containsMatchIn(combinedText)
        val hasExplicitYouPaid = Regex("""(?i)\b(?:you\s+paid|you\s+sent|paid\s+to|paid\s+at|sent\s+to|debited)\b""").containsMatchIn(combinedText)

        val type = when {
            hasExplicitPaidYou && !hasExplicitYouPaid -> "Income"
            hasExplicitYouPaid && !hasExplicitPaidYou -> "Expense"
            hasIncome && !hasExpense -> "Income"
            hasExpense && !hasIncome -> "Expense"
            else -> {
                val incomeMatch = INCOME_REGEX.find(combinedText)
                val expenseMatch = EXPENSE_REGEX.find(combinedText)
                if (incomeMatch != null && expenseMatch != null) {
                    if (incomeMatch.range.first < expenseMatch.range.first) "Income" else "Expense"
                } else if (hasIncome) {
                    "Income"
                } else {
                    "Expense"
                }
            }
        }

        // 3. Clean text by removing balances & card/account numbers so they aren't parsed as transaction amounts
        val strippedText = combinedText
            .replace(BALANCE_REGEX, " ")
            .replace(BALANCE_SIMPLE_REGEX, " ")
            .replace(ACCOUNT_CARD_REGEX, " ")

        // 4. Extract amount and currency
        val amountMatch = AMOUNT_PATTERN.find(strippedText) ?: return null

        val rawCurrency = (amountMatch.groups[1]?.value ?: amountMatch.groups[4]?.value ?: amountMatch.groups[5]?.value ?: amountMatch.groups[7]?.value ?: "").trim()
        val rawAmountStr = (amountMatch.groups[2]?.value ?: amountMatch.groups[3]?.value ?: amountMatch.groups[6]?.value ?: "").trim()

        val amount = parseAmount(rawAmountStr) ?: return null
        if (amount <= 0.0) return null

        val (currencyCode, currencySymbol) = normalizeCurrency(rawCurrency)

        // 5. Extract merchant / payee name and determine category
        val rawMerchant = extractMerchant(title, fullBody, packageName)
        val category = mapCategory(rawMerchant, combinedText, type)

        val appNames = listOf(
            "google pay", "phonepe", "paytm", "bhim", "paypal", "venmo", "cash app",
            "zelle", "revolut", "wise", "monzo", "apple pay", "wallet", "messages", "sms",
            "message", "messaging"
        )
        val titleIsAppName = appNames.any { title.contains(it, ignoreCase = true) }

        val displayTitle = if (!rawMerchant.isNullOrBlank()) {
            if (type == "Expense") {
                if (rawMerchant.startsWith("Paid to", ignoreCase = true) || rawMerchant.startsWith("Paid at", ignoreCase = true)) {
                    rawMerchant
                } else {
                    "Paid to $rawMerchant"
                }
            } else {
                if (rawMerchant.startsWith("Received from", ignoreCase = true)) {
                    rawMerchant
                } else {
                    "Received from $rawMerchant"
                }
            }
        } else {
            val fallbackBank = if (!titleIsAppName && title.isNotBlank() && title.length in 2..30) {
                cleanMerchantName(title).ifBlank { null }
            } else null

            if (fallbackBank != null) {
                if (type == "Expense") "Paid via $fallbackBank" else "Received via $fallbackBank"
            } else {
                if (type == "Expense") "Paid via UPI" else "Received via UPI"
            }
        }

        return ParsedTransaction(
            title = displayTitle,
            amount = amount,
            type = type,
            category = category,
            currencyCode = currencyCode,
            originalCurrencySymbol = currencySymbol,
            rawMerchant = rawMerchant,
            timestamp = postTime
        )
    }

    /**
     * Parses amounts with support for US/Indian formats (1,250.50) and European formats (19,99 or 1.250,50).
     */
    fun parseAmount(raw: String): Double? {
        val clean = raw.trim().trimEnd('.', ',')
        if (clean.isBlank()) return null
        return try {
            if (clean.contains(",") && clean.contains(".")) {
                val lastComma = clean.lastIndexOf(',')
                val lastDot = clean.lastIndexOf('.')
                if (lastComma > lastDot) {
                    // European style e.g. 1.250,50 -> 1250.50
                    clean.replace(".", "").replace(",", ".").toDouble()
                } else {
                    // US / Indian style e.g. 1,250.50 -> 1250.50
                    clean.replace(",", "").toDouble()
                }
            } else if (clean.contains(",")) {
                val commaIndex = clean.lastIndexOf(',')
                val digitsAfter = clean.length - commaIndex - 1
                if (digitsAfter in 1..2) {
                    // European decimal e.g. 19,99 -> 19.99
                    clean.replace(",", ".").toDouble()
                } else {
                    // Thousand separator e.g. 1,000 -> 1000.00
                    clean.replace(",", "").toDouble()
                }
            } else {
                clean.toDouble()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizeCurrency(raw: String): Pair<String?, String?> {
        val upper = raw.uppercase(Locale.US).trim()
        return when {
            upper == "₹" || upper == "RS." || upper == "RS" || upper == "INR" -> Pair("INR", "₹")
            upper == "$" || upper == "USD" -> Pair("USD", "$")
            upper == "€" || upper == "EUR" -> Pair("EUR", "€")
            upper == "£" || upper == "GBP" -> Pair("GBP", "£")
            upper == "AED" -> Pair("AED", "AED")
            upper == "SAR" -> Pair("SAR", "﷼")
            upper == "SGD" -> Pair("SGD", "S$")
            upper == "AUD" -> Pair("AUD", "A$")
            upper == "CAD" -> Pair("CAD", "C$")
            upper == "JPY" || upper == "¥" -> Pair("JPY", "¥")
            else -> Pair(upper.ifBlank { null }, raw.ifBlank { null })
        }
    }

    /**
     * Extracts merchant or sender name from notification title/body using financial regex rules.
     */
    private fun extractMerchant(title: String, body: String, packageName: String): String? {
        val appNames = listOf(
            "google pay", "phonepe", "paytm", "bhim", "paypal", "venmo", "cash app",
            "zelle", "revolut", "wise", "monzo", "apple pay", "wallet", "messages", "sms",
            "message", "messaging"
        )
        val titleIsAppName = appNames.any { title.contains(it, ignoreCase = true) }

        val candidatePatterns = listOf(
            // "to VPA: <upi_id>" or "to VPA <upi_id>"
            Regex("""(?i)\b(?:to|at)\s+(?:vpa\s*:?\s*)([A-Za-z0-9\.\-_@]+)"""),
            // "from VPA: <upi_id>" or "from VPA <upi_id>"
            Regex("""(?i)\b(?:from|by)\s+(?:vpa\s*:?\s*)([A-Za-z0-9\.\-_@]+)"""),
            // "VPA <upi_id>"
            Regex("""(?i)\bvpa\s*:?\s*([A-Za-z0-9\.\-_@]+)"""),
            // Specific recipient matching after transfer tokens, ignoring noise tokens e.g. "trf to Rahul Sharma Refno..."
            Regex("""(?i)\b(?:paid to|sent to|trf to|transferred to|toward[s]?)\s+([A-Za-z][A-Za-z0-9\s&'’\.\-_]{1,40}?)(?:\s+(?:via\s+upi|using\s+upi|with\s+upi|ref|refno|rrn|avl|bal|on\s+\d|\.|\band\b|$))"""),
            // "<Sender> (has) paid / sent you" e.g. "AJAZ Ahmad WANI paid you 101.00" or "Ajaz sent you ₹101"
            Regex("""(?i)\b([A-Za-z0-9\s&'’\.\-_]+?)\s+(?:has\s+)?(?:paid|sent)\s+you\b"""),
            // "sent ... to <Recipient>" e.g. "sent Rs.500 to Rahul Sharma"
            Regex("""(?i)\bsent\s+(?:(?:[₹$€£]|Rs\.?|INR|USD|EUR|GBP|AED|SAR|SGD|AUD|CAD|JPY)\s*[\d,.]+|[\d,.]+\s*(?:[₹$€£]|Rs\.?|INR|USD|EUR|GBP|AED|SAR|SGD|AUD|CAD|JPY)\s*)?(?:of\s+[^.]*?)?to\s+([A-Za-z0-9\s&'’\.\-_@]+?)(?:\s+on\s+\d|\s+ref|\s+refno|\s+rrn|\s+avl|\s+bal|\.|\band\b|$)"""),
            // "paid ... to/at <Merchant>" e.g. "paid ₹250 to Swiggy"
            Regex("""(?i)\bpaid\s+(?:(?:[₹$€£]|Rs\.?|INR|USD|EUR|GBP|AED|SAR|SGD|AUD|CAD|JPY)\s*[\d,.]+|[\d,.]+\s*(?:[₹$€£]|Rs\.?|INR|USD|EUR|GBP|AED|SAR|SGD|AUD|CAD|JPY)\s*)?(?:of\s+[^.]*?)?(?:to|at)\s+([A-Za-z0-9\s&'’\.\-_@]+?)(?:\s+on\s+\d|\s+ref|\s+refno|\s+rrn|\s+avl|\s+bal|\.|\band\b|$)"""),
            // "received ... from <Sender>" e.g. "received Rs.1,000 from Amit Kumar"
            Regex("""(?i)\breceived\s+(?:(?:[₹$€£]|Rs\.?|INR|USD|EUR|GBP|AED|SAR|SGD|AUD|CAD|JPY)\s*[\d,.]+|[\d,.]+\s*(?:[₹$€£]|Rs\.?|INR|USD|EUR|GBP|AED|SAR|SGD|AUD|CAD|JPY)\s*)?(?:of\s+[^.]*?)?from\s+([A-Za-z0-9\s&'’\.\-_@]+?)(?:\s+on\s+\d|\s+ref|\s+refno|\s+rrn|\s+avl|\s+bal|\.|\band\b|$)"""),
            // "credited ... from (VPA) <Sender>" e.g. "credited ... from VPA abc@ybl"
            Regex("""(?i)\bcredited\s+.*?(?:(?:by\s+)?transfer\s+from|from|by)\s+(?!(?:your\s+)?(?:a/c|acct|account|card|bank)\b)(?:vpa\s*:?\s*)?([A-Za-z0-9\s&'’\.\-_@]+?)(?:\s+to|\s+on\s+\d|\s+ref|\s+avl|\s+bal|\.|\band\b|$)"""),
            // "debited ... to (VPA) <Recipient>" e.g. "debited ... to VPA xyz@okicici"
            Regex("""(?i)\bdebited\s+.*?(?:to|towards)\s+(?!(?:your\s+)?(?:a/c|acct|account|card|bank)\b)(?:vpa\s*:?\s*)?([A-Za-z0-9\s&'’\.\-_@]+?)(?:\s+successful|\s+using|\s+via|\s+from|\s+on\s+\d|\s+ref|\s+refno|\s+rrn|\s+avl|\s+bal|\.|\band\b|$)"""),
            // "to/at <Merchant>"
            Regex("""(?i)\b(?:to|at)\s+([A-Za-z0-9\s&'’\.\-_@]+?)(?:\s+successful|\s+using|\s+via|\s+from|\s+on\s+\d|\s+ref|\s+refno|\s+rrn|\s+avl|\s+bal|\.|\band\b|$)"""),
            // "(by transfer) from <Sender>"
            Regex("""(?i)\b(?:(?:by\s+)?transfer\s+from|from|by)\s+(?!(?:your\s+)?(?:a/c|acct|account|card|bank)\b)([A-Za-z0-9\s&'’\.\-_@]+?)(?:\s+to|\s+on\s+\d|\s+ref|\s+avl|\s+bal|\.|\band\b|$)"""),
            // "Info: <Merchant>" e.g. Info: UPI/Uber or Info: Amazon Retail
            Regex("""(?i)\b(?:info:?|ref:?)\s*([A-Za-z0-9\s&'’\.\-_/]+?)(?:\s*\.|\s+Total|\s+Avl|\s+Bal|$)"""),
            // "<Sender> has sent / transferred / credited to you or account"
            Regex("""(?i)\b([A-Za-z0-9\s&'’\.\-_]+?)\s+(?:has\s+)?(?:sent|transferred|credited)\s+(?:to\s+you|in|into|to)\s+(?:your\s+)?([A-Za-z0-9\s]+?)(?:account|a/c|\.|$)"""),
            // "<Sender> deposited in your <Bank> account"
            Regex("""(?i)\b([A-Za-z0-9\s&'’\.\-_]+?)\s+(?:has\s+)?deposited\s+(?:in|into|to)\s+(?:your\s+)?([A-Za-z0-9\s]+?)(?:account|a/c|\.|$)""")
        )

        val fullText = "$title $body"
        for (pattern in candidatePatterns) {
            val match = pattern.find(fullText)
            if (match != null) {
                var candidate = match.groupValues[1].trim()
                if (candidate.contains("/")) {
                    candidate = candidate.substringAfterLast("/").trim()
                }
                if (candidate.startsWith("UPI-", ignoreCase = true)) {
                    candidate = candidate.substring(4).trim()
                }
                val cleaned = cleanMerchantName(candidate)
                if (cleaned.isNotBlank() && cleaned.length >= 2) {
                    return cleaned
                }
            }
        }

        // If title is a distinct entity and not an app name, clean and use title
        if (!titleIsAppName && title.isNotBlank() && title.length in 2..50) {
            val cleanedTitle = cleanMerchantName(title)
            if (cleanedTitle.isNotBlank()) return cleanedTitle
        }

        return null
    }

    private fun cleanMerchantName(name: String): String {
        val trimmed = name.trim()

        // 1. If pure phone number (e.g. 10 to 12 digits or with +91)
        if (trimmed.matches(Regex("""^\+?\d{10,13}$"""))) {
            val digits = trimmed.removePrefix("+91").removePrefix("+")
            return "UPI Transfer ($digits)"
        }

        // If candidate is a date format like "22-Nov-26" or "22/11/2026", discard
        if (trimmed.matches(Regex("""(?i)^\d{1,2}[-/\s](?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec|\d{1,2})[-/\s]\d{2,4}$"""))) {
            return ""
        }

        // If candidate is simply an account or card label, discard
        if (trimmed.matches(Regex("""(?i)^(?:your\s+)?(?:a/c|acct|account|card|bank)\b.*$"""))) {
            return ""
        }

        // If candidate contains an embedded amount like "Rs. 101.00" or "$50", it's a dirty sentence match
        if (trimmed.contains(Regex("""(?i)(?:[₹$€£]|Rs\.?|INR|USD|EUR|GBP|AED)\s*[\d,.]+"""))) {
            return ""
        }

        // Discard pure numeric strings or reference IDs like "92156..." or "Ref 12345"
        if (trimmed.matches(Regex("""^(?i)(?:ref(?:no)?\.?\s*)?\d{4,}$"""))) {
            return ""
        }

        // 2. Strip banking boilerplate phrases & trailing "paid you 101.00", "sent you", etc.
        var stripped = trimmed
            .replace(Regex("""(?i)^(?:(?:by\s+)?transfer\s+from|from|by)\s+"""), "")
            .replace(Regex("""(?i)\s+(?:has\s+)?(?:paid|sent|transferred)\s+you(?:\s+(?:[₹$€£]|Rs\.?|INR|USD|EUR|GBP|AED|SAR|SGD|AUD|CAD|JPY)?\s*[\d,.]+)?.*$"""), "")
            .replace(Regex("""(?i)\s+(?:has\s+)?(?:paid|sent|transferred)\s+(?:to\s+you|from).*$"""), "")
            .replace(Regex("""(?i)\b(?:deposited\s+in|deposited\s+into|credited\s+to|debited\s+from|transferred\s+to|spent\s+on)\s+(?:your\s+)?[A-Za-z0-9\s]+?(?:account|a/c|card|bank)\b.*$"""), "")
            .replace(Regex("""(?i)\b(?:in|into|from|to)\s+your\s+[A-Za-z0-9\s]+?(?:account|a/c|bank)\b.*$"""), "")
            .replace(Regex("""(?i)\s*\[?(?:INR|USD|EUR|GBP|AED|SAR|Rs\.?|₹|\$)\]?\s*"""), " ")
            .replace(Regex("""(?i)^(the|m/s|vpa)\s+"""), "")
            .replace(Regex("""(?i)\s+(?:successful|completed|via\s+upi|using\s+upi|with\s+upi|upi\s+ref|ref\s*no|refno|rrn).*$"""), "")
            .replace(Regex("""[.,\-_/]+$"""), "")
            .trim()

        // Explicitly check for generic noise tokens that shouldn't serve as a person/merchant name alone
        val noiseTokens = setOf("UPI", "UPI TRANSFER", "BANK", "ACCOUNT", "A/C", "VPA", "REF", "IMPS", "NEFT", "RTGS", "PAYMENT", "TRANSFER")
        if (noiseTokens.contains(stripped.uppercase(Locale.US))) {
            return ""
        }

        // If stripped is empty or just generic word
        if (stripped.isBlank() || stripped.equals("Deposited", ignoreCase = true) || stripped.equals("Credited", ignoreCase = true) || stripped.equals("Debited", ignoreCase = true)) {
            val upperName = name.uppercase(Locale.US)
            return when {
                upperName.contains("ICICI") -> "ICICI Bank"
                upperName.contains("HDFC") -> "HDFC Bank"
                upperName.contains("SBI") -> "SBI"
                upperName.contains("AXIS") -> "Axis Bank"
                upperName.contains("KOTAK") -> "Kotak Bank"
                upperName.contains("PAYTM") -> "Paytm"
                else -> ""
            }
        }

        // 3. Format words: Title Case for regular words, preserve uppercase for short acronyms
        val acronyms = setOf("ICICI", "HDFC", "SBI", "PNB", "BOB", "UPI", "ATM", "POS", "VPA", "NEFT", "RTGS", "IMPS", "LTD", "PVT", "LLP")
        return stripped
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                if (word.contains("@")) {
                    word.lowercase(Locale.US)
                } else {
                    val upperWord = word.uppercase(Locale.US)
                    if (acronyms.contains(upperWord) || (word.length <= 4 && word.all { it.isLetter() } && word.all { it.isUpperCase() })) {
                        upperWord
                    } else {
                        word.lowercase(Locale.US).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
                    }
                }
            }
    }

    /**
     * Maps merchant name or transaction text to Vaulta's predefined categories.
     */
    fun mapCategory(merchant: String?, fullText: String, type: String): String {
        val text = "${merchant ?: ""} $fullText".lowercase(Locale.US)

        return when {
            // Food & Dining
            listOf("swiggy", "zomato", "mcdonald", "kfc", "starbucks", "domino", "uber eats",
                "doordash", "restaurant", "cafe", "burger", "pizza", "bakery", "diner", "subway",
                "taco", "chipotle", "chai point", "food", "dining", "barista").any { text.contains(it) } -> "Food"

            // Grocery
            listOf("blinkit", "zepto", "instamart", "walmart", "target", "groceries", "grocery",
                "supermarket", "mart", "bigbasket", "costco", "trader joe", "whole foods",
                "safeway", "tesco", "carrefour", "spencer").any { text.contains(it) } -> "Grocery"

            // Transport
            listOf("uber", "ola", "lyft", "grab", "taxi", "cab", "metro", "fuel", "petrol",
                "diesel", "shell", "gas station", "irctc", "transit", "parking", "train",
                "bus", "toll", "fastag", "chevron", "bp").any { text.contains(it) } -> "Transport"

            // Shopping
            listOf("amazon", "flipkart", "myntra", "ebay", "zara", "h&m", "shop", "retail",
                "store", "aliexpress", "etsy", "nike", "adidas", "apple store", "clothing",
                "fashion", "ajio", "nykaa").any { text.contains(it) } -> "Shopping"

            // Utilities & Bills
            listOf("electric", "water", "gas", "electricity", "broadband", "wifi", "utility",
                "utilities", "billdesk", "airtel", "jio", "vi", "recharge", "telecom", "power",
                "bill", "postpaid", "prepaid", "bescom", "tneb").any { text.contains(it) } -> "Utilities"

            // Entertainment
            listOf("netflix", "spotify", "youtube", "hotstar", "hulu", "disney", "prime video",
                "cinema", "movie", "bookmyshow", "steam", "playstation", "xbox", "nintendo",
                "pvr", "inox").any { text.contains(it) } -> "Entertainment"

            // Healthcare
            listOf("pharmacy", "hospital", "clinic", "apollo", "1mg", "medical", "medicine",
                "doctor", "dental", "walgreens", "cvs", "chemist", "pharmeasy").any { text.contains(it) } -> "Healthcare"

            // Income Specifics
            type == "Income" && listOf("salary", "payroll", "wage", "stipend").any { text.contains(it) } -> "Salary"
            type == "Income" && listOf("freelance", "upwork", "fiverr").any { text.contains(it) } -> "Freelance"
            type == "Income" && listOf("dividend", "interest", "stock", "mutual fund").any { text.contains(it) } -> "Investments"
            type == "Income" && listOf("cashback", "reward").any { text.contains(it) } -> "Other Income"

            // Fallback
            type == "Income" -> "Other Income"
            else -> "Other Expenses"
        }
    }
}
