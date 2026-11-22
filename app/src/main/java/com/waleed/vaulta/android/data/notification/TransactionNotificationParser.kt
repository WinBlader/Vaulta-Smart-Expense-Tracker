package com.waleed.vaulta.android.data.notification

import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

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
 * Thread-safe deduplication engine to suppress duplicate transaction logs
 * (e.g. when Bank SMS and UPI/Wallet push alert fire simultaneously within 2 minutes).
 */
object TransactionDeduplicator {
    private const val DEDUP_WINDOW_MILLIS = 120_000L // 2 minutes
    private val recentTransactions = ConcurrentHashMap<String, Long>()

    @Synchronized
    fun isDuplicate(
        amount: Double,
        type: String,
        currentTime: Long = System.currentTimeMillis()
    ): Boolean {
        // Purge expired records older than the deduplication window
        val iterator = recentTransactions.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (currentTime - entry.value > DEDUP_WINDOW_MILLIS) {
                iterator.remove()
            }
        }

        val key = "${type.uppercase(Locale.US)}_${String.format(Locale.US, "%.2f", amount)}"
        val lastSeen = recentTransactions[key]
        if (lastSeen != null && (currentTime - lastSeen) <= DEDUP_WINDOW_MILLIS) {
            return true
        }

        recentTransactions[key] = currentTime
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

    // Known financial & messaging package prefixes or names
    private val SUPPORTED_PACKAGES = setOf(
        // SMS & RCS Apps
        "com.google.android.apps.messaging",
        "com.android.mms",
        "com.samsung.android.messaging",
        "com.oneplus.mms",
        "com.miui.mms",
        "com.coloros.mms",
        "com.vivo.mms",
        // Indian UPI & Wallets
        "com.google.android.apps.nbu.paisa.user", // Google Pay India
        "com.phonepe.app",                       // PhonePe
        "net.one97.paytm",                        // Paytm
        "in.org.npci.upiapp",                     // BHIM
        "in.amazon.mShop.android.shopping",       // Amazon India
        // International Payment Apps & Digital Banks
        "com.venmo",
        "com.squareup.cash",                      // Cash App
        "com.paypal.android.p2pmobile",           // PayPal
        "com.zellepay.zelle",                     // Zelle
        "com.revolut.revolut",                    // Revolut
        "com.transferwise.android",               // Wise
        "com.monzo.android",                      // Monzo
        "com.starlingbank.android",               // Starling Bank
        "com.chime.app",                          // Chime
        "com.apple.android.wallet",
        "com.google.android.apps.walletnfcrel"    // Google Wallet
    )

    // Strict Noise & Scam Filter: OTPs, 2FA, Failed, Declined, Requests, and Promos
    private val NOISE_PATTERNS = listOf(
        // OTPs & Auth codes
        Regex("""(?i)\b(otp|one[- ]time password|verification code|security code|2fa|do not share|secret code)\b"""),
        // Failed / Declined / Reversals
        Regex("""(?i)\b(failed|declined|unsuccessful|reversed due to failure|insufficient funds|cancelled|canceled|transaction rejected|payment failed)\b"""),
        // Incoming payment requests / mandates
        Regex("""(?i)\b(requested|has requested|collect request|payment request|autopay mandate created|mandate registered|request received)\b"""),
        // Marketing & Promotional
        Regex("""(?i)\b(win up to|flat \d+% off|pre[- ]approved loan|apply now|voucher|cashback of up to|congratulations you won|claim your|special offer)\b""")
    )

    // Keywords that indicate banking or payment context
    private val FINANCIAL_KEYWORDS = listOf(
        "debited", "debit", "credited", "credit", "paid", "spent", "received", "transferred",
        "transfer to", "transfer from", "withdrawn", "withdrawal", "deposit", "deposited",
        "charged", "charge", "refund", "refunded", "cashback", "salary", "payout", "pos txn",
        "card ending", "a/c", "acct", "account", "bank", "upi", "vpa", "avl bal", "available balance"
    )

    // Expense indicators
    private val EXPENSE_REGEX = Regex(
        """(?i)\b(debited|debit|paid|sent|spent|withdrawn|withdrawal|purchased|purchase|charged|charge|payment of|payment to|transfer to|transferred to|pos txn|atm|sent to|you paid|you sent)\b"""
    )

    // Income indicators
    private val INCOME_REGEX = Regex(
        """(?i)\b(credited|credit|received|deposited|deposit|refund|refunded|cashback|salary|payout|transfer from|transferred from|received from|you received)\b"""
    )

    // Balances & card/account numbers to strip before amount search
    private val BALANCE_REGEX = Regex(
        """(?i)\b(?:(?:total|available|avl\.?)\s+)*bal(?:ance)?\b.*?(?:([₹$€£]|Rs\.?|INR|USD|EUR|GBP|AED|SAR|SGD|AUD|CAD|JPY)\s*(\d[\d,.]*)|\b(\d[\d,.]*)\s*([₹$€£]|Rs\.?|INR|USD|EUR|GBP|AED|SAR|SGD|AUD|CAD|JPY))""",
        RegexOption.IGNORE_CASE
    )

    private val ACCOUNT_CARD_REGEX = Regex(
        """(?i)\b(?:a/c(?:\s*no\.?)?|account(?:\s*no\.?)?|card(?:\s*ending(?:\s*in)?)?)\s*(?:[*xX\d]+)""",
        RegexOption.IGNORE_CASE
    )

    // Currency & Amount extraction pattern
    // Supported currencies: ₹, Rs., Rs, INR, $, USD, €, EUR, £, GBP, AED, SAR, SGD, AUD, CAD, JPY
    private val AMOUNT_PATTERN = Regex(
        """(?i)(?:([₹$€£]|Rs\.?|INR|USD|EUR|GBP|AED|SAR|SGD|AUD|CAD|JPY)\s*(\d[\d,.]*)|\b(\d[\d,.]*)\s*([₹$€£]|Rs\.?|INR|USD|EUR|GBP|AED|SAR|SGD|AUD|CAD|JPY))"""
    )

    /**
     * Parses a notification from its package, title, and body text.
     * Returns a valid [ParsedTransaction] or null if the notification is not a completed financial transaction.
     */
    fun parse(
        packageName: String,
        title: String,
        text: String,
        bigText: String? = null,
        postTime: Long = System.currentTimeMillis()
    ): ParsedTransaction? {
        val fullBody = if (!bigText.isNullOrBlank() && bigText.length > text.length) bigText else text
        val combinedText = "$title $fullBody".trim()

        if (combinedText.isBlank()) return null

        // 1. Package source check & financial intent verification
        val isKnownPackage = SUPPORTED_PACKAGES.contains(packageName) ||
                packageName.contains("messaging", ignoreCase = true) ||
                packageName.contains(".mms", ignoreCase = true) ||
                packageName.contains(".sms", ignoreCase = true)

        val hasFinancialKeywords = FINANCIAL_KEYWORDS.any { combinedText.contains(it, ignoreCase = true) }

        if (!isKnownPackage && !hasFinancialKeywords) {
            return null
        }

        // 2. Strict noise, scam, OTP, and failure filtering
        for (pattern in NOISE_PATTERNS) {
            if (pattern.containsMatchIn(combinedText)) {
                return null
            }
        }

        // 3. Classify transaction type (Income vs Expense)
        val hasIncome = INCOME_REGEX.containsMatchIn(combinedText)
        val hasExpense = EXPENSE_REGEX.containsMatchIn(combinedText)

        if (!hasIncome && !hasExpense) {
            return null
        }

        // Priority resolution: if both words appear (e.g. "debited ... refund" vs "credited ... paid")
        val type = when {
            hasIncome && !hasExpense -> "Income"
            hasExpense && !hasIncome -> "Expense"
            else -> {
                // Determine which verb appears earlier or matches specific keywords
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

        // 4. Clean text by removing balances & card/account numbers so they aren't parsed as amounts
        val strippedText = combinedText
            .replace(BALANCE_REGEX, " ")
            .replace(ACCOUNT_CARD_REGEX, " ")

        // 5. Extract amount and currency
        val amountMatch = AMOUNT_PATTERN.find(strippedText) ?: return null

        val rawCurrency = (amountMatch.groups[1]?.value ?: amountMatch.groups[4]?.value ?: "").trim()
        val rawAmountStr = (amountMatch.groups[2]?.value ?: amountMatch.groups[3]?.value ?: "").trim()

        val amount = parseAmount(rawAmountStr) ?: return null
        if (amount <= 0.0) return null

        val (currencyCode, currencySymbol) = normalizeCurrency(rawCurrency)

        // 6. Extract merchant / payee name and determine category
        val rawMerchant = extractMerchant(title, fullBody, packageName)
        val category = mapCategory(rawMerchant, combinedText, type)

        val displayTitle = rawMerchant?.ifBlank { null }
            ?: (if (type == "Income") "Income" else "Expense")

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
        // App-specific title handling:
        // In GPay, PhonePe, Paytm, Venmo, Cash App, PayPal, the title is often the Merchant/Payee name!
        val appNames = listOf(
            "google pay", "phonepe", "paytm", "bhim", "paypal", "venmo", "cash app",
            "zelle", "revolut", "wise", "monzo", "apple pay", "wallet", "messages", "sms"
        )
        val titleIsAppName = appNames.any { title.contains(it, ignoreCase = true) }

        val candidatePatterns = listOf(
            // "<Sender> deposited in your <Bank> account" e.g. "NAVI LIMITED Deposited IN YOUR ICICI BANK Account"
            Regex("""(?i)\b([A-Za-z0-9\s&'’\.\-_]+?)\s+(?:has\s+)?deposited\s+(?:in|into|to)\s+(?:your\s+)?([A-Za-z0-9\s]+?)(?:account|a/c|\.|$)"""),
            // "<Sender> has sent / transferred / credited"
            Regex("""(?i)\b([A-Za-z0-9\s&'’\.\-_]+?)\s+(?:has\s+)?(?:sent|transferred|credited)\s+(?:to|in|into)\s+(?:your\s+)?([A-Za-z0-9\s]+?)(?:account|a/c|\.|$)"""),
            // "paid ... to <Merchant>" / "payment of ... to <Merchant>" / "sent ... to <Merchant>"
            Regex("""(?i)\b(?:paid|sent|spent|payment|transfer|transferred)\s+(?:(?:[₹$€£]|Rs\.?|INR|USD|EUR|GBP|AED|SAR|SGD|AUD|CAD|JPY)\s*[\d,.]+|[\d,.]+\s*(?:[₹$€£]|Rs\.?|INR|USD|EUR|GBP|AED|SAR|SGD|AUD|CAD|JPY)\s*)?(?:of\s+[^.]*?)?(?:to|at)\s+([A-Za-z0-9\s&'’\.\-_]+?)(?:\s+successful|\s+using|\s+via|\s+from|\s+on\s+\d|\s+ref|\s+avl|\s+bal|\.|\band\b|$)"""),
            // "received ... from <Sender>" / "credited ... by <Sender>"
            Regex("""(?i)\b(?:received|credited|deposit|deposited)\s+(?:(?:[₹$€£]|Rs\.?|INR|USD|EUR|GBP|AED|SAR|SGD|AUD|CAD|JPY)\s*[\d,.]+|[\d,.]+\s*(?:[₹$€£]|Rs\.?|INR|USD|EUR|GBP|AED|SAR|SGD|AUD|CAD|JPY)\s*)?(?:from|by)\s+([A-Za-z0-9\s&'’\.\-_]+?)(?:\s+to|\s+on\s+\d|\s+ref|\s+avl|\s+bal|\.|\band\b|$)"""),
            // "to <Merchant>"
            Regex("""(?i)\b(?:to)\s+([A-Za-z0-9\s&'’\.\-_]+?)(?:\s+successful|\s+using|\s+via|\s+from|\s+on\s+\d|\s+ref|\s+avl|\s+bal|\.|\band\b|$)"""),
            // "at <Merchant>"
            Regex("""(?i)\b(?:at)\s+([A-Za-z0-9\s&'’\.\-_]+?)(?:\s+with|\s+using|\s+on\s+\d|\s+ref|\s+avl|\s+bal|\.|\band\b|$)"""),
            // "from <Sender>"
            Regex("""(?i)\b(?:from)\s+([A-Za-z0-9\s&'’\.\-_]+?)(?:\s+to|\s+on\s+\d|\s+ref|\s+avl|\s+bal|\.|\band\b|$)"""),
            // "Info: <Merchant>" e.g. Info: UPI/Uber or Info: Amazon
            Regex("""(?i)\b(?:info:?)\s*([A-Za-z0-9\s&'’\.\-_/]+?)(?:\s*\.|\s+Total|\s+Avl|$)"""),
            // "VPA <upi_id>"
            Regex("""(?i)\b(?:vpa)\s+([A-Za-z0-9\.\-_@]+)""")
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

        // If title is a distinct entity and not an app name, use title
        if (!titleIsAppName && title.isNotBlank() && title.length in 2..35) {
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

        // 2. Strip banking boilerplate phrases
        var stripped = trimmed
            .replace(Regex("""(?i)\b(?:deposited\s+in|deposited\s+into|credited\s+to|debited\s+from|transferred\s+to|spent\s+on)\s+(?:your\s+)?[A-Za-z0-9\s]+?(?:account|a/c|card|bank)\b.*$"""), "")
            .replace(Regex("""(?i)\b(?:in|into|from|to)\s+your\s+[A-Za-z0-9\s]+?(?:account|a/c|bank)\b.*$"""), "")
            .replace(Regex("""(?i)\s*\[?(?:INR|USD|EUR|GBP|AED|SAR|Rs\.?|₹|\$)\]?\s*"""), " ")
            .replace(Regex("""(?i)^(the|m/s|vpa)\s+"""), "")
            .replace(Regex("""(?i)\s+(successful|completed|via\s+upi|using\s+upi|with\s+upi).*$"""), "")
            .replace(Regex("""[.,\-_/]+$"""), "")
            .trim()

        // If stripped is empty or just generic word
        if (stripped.isBlank() || stripped.equals("Deposited", ignoreCase = true) || stripped.equals("Credited", ignoreCase = true)) {
            val upperName = name.uppercase(Locale.US)
            return when {
                upperName.contains("ICICI") -> "ICICI Bank Deposit"
                upperName.contains("HDFC") -> "HDFC Bank Deposit"
                upperName.contains("SBI") -> "SBI Deposit"
                upperName.contains("AXIS") -> "Axis Bank Deposit"
                upperName.contains("KOTAK") -> "Kotak Bank Deposit"
                upperName.contains("PAYTM") -> "Paytm Deposit"
                else -> ""
            }
        }

        // 3. Format words: Title Case for regular words, preserve uppercase for short acronyms
        val acronyms = setOf("ICICI", "HDFC", "SBI", "PNB", "BOB", "UPI", "ATM", "POS", "VPA", "NEFT", "RTGS", "IMPS", "LTD", "PVT", "LLP")
        return stripped
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                val upperWord = word.uppercase(Locale.US)
                if (acronyms.contains(upperWord) || (word.length <= 4 && word.all { it.isLetter() } && word.all { it.isUpperCase() })) {
                    upperWord
                } else {
                    word.lowercase(Locale.US).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
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
