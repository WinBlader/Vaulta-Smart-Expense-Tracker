package com.waleed.vaulta.android.data

import android.content.Context
import android.content.SharedPreferences
import com.waleed.vaulta.android.data.model.CurrencyItem
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

object CurrencyManager {
    private const val PREFS_NAME = "vaulta_currency_prefs"
    private const val KEY_CURRENCY_CODE = "currency_code"

    val defaultCurrency = CurrencyItem(
        code = "USD",
        symbol = "$",
        name = "US Dollar",
        country = "United States",
        flag = "🇺🇸",
        category = "Dollar"
    )

    val supportedCurrencies: List<CurrencyItem> = listOf(
        // Dollars
        CurrencyItem("USD", "$", "US Dollar", "United States", "🇺🇸", "Dollar"),
        CurrencyItem("CAD", "C$", "Canadian Dollar", "Canada", "🇨🇦", "Dollar"),
        CurrencyItem("AUD", "A$", "Australian Dollar", "Australia", "🇦🇺", "Dollar"),
        CurrencyItem("NZD", "NZ$", "New Zealand Dollar", "New Zealand", "🇳🇿", "Dollar"),
        CurrencyItem("SGD", "S$", "Singapore Dollar", "Singapore", "🇸🇬", "Dollar"),
        CurrencyItem("HKD", "HK$", "Hong Kong Dollar", "Hong Kong", "🇭🇰", "Dollar"),

        // Rupees & South Asian
        CurrencyItem("INR", "₹", "Indian Rupee", "India", "🇮🇳", "Rupee"),
        CurrencyItem("PKR", "₨", "Pakistani Rupee", "Pakistan", "🇵🇰", "Rupee"),
        CurrencyItem("NPR", "रू", "Nepalese Rupee", "Nepal", "🇳🇵", "Rupee"),
        CurrencyItem("LKR", "Rs", "Sri Lankan Rupee", "Sri Lanka", "🇱🇰", "Rupee"),
        CurrencyItem("BDT", "৳", "Bangladeshi Taka", "Bangladesh", "🇧🇩", "Rupee"),

        // Pounds
        CurrencyItem("GBP", "£", "British Pound", "United Kingdom", "🇬🇧", "Pound"),
        CurrencyItem("EGP", "E£", "Egyptian Pound", "Egypt", "🇪🇬", "Pound"),
        CurrencyItem("LBP", "L£", "Lebanese Pound", "Lebanon", "🇱🇧", "Pound"),

        // Riyals
        CurrencyItem("SAR", "﷼", "Saudi Riyal", "Saudi Arabia", "🇸🇦", "Riyal"),
        CurrencyItem("QAR", "﷼", "Qatari Riyal", "Qatar", "🇶🇦", "Riyal"),
        CurrencyItem("OMR", "﷼", "Omani Rial", "Oman", "🇴🇲", "Riyal"),
        CurrencyItem("YER", "﷼", "Yemeni Rial", "Yemen", "🇾🇪", "Riyal"),
        CurrencyItem("IRR", "﷼", "Iranian Rial", "Iran", "🇮🇷", "Riyal"),

        // Dirhams
        CurrencyItem("AED", "AED", "UAE Dirham", "United Arab Emirates", "🇦🇪", "Dirham"),
        CurrencyItem("MAD", "MAD", "Moroccan Dirham", "Morocco", "🇲🇦", "Dirham"),

        // Dinars
        CurrencyItem("KWD", "KD", "Kuwaiti Dinar", "Kuwait", "🇰🇼", "Dinar"),
        CurrencyItem("BHD", "BD", "Bahraini Dinar", "Bahrain", "🇧🇭", "Dinar"),
        CurrencyItem("JOD", "JD", "Jordanian Dinar", "Jordan", "🇯🇴", "Dinar"),
        CurrencyItem("IQD", "IQD", "Iraqi Dinar", "Iraq", "🇮🇶", "Dinar"),

        // Euro
        CurrencyItem("EUR", "€", "Euro", "European Union", "🇪🇺", "Euro"),

        // Asian & Global Currencies
        CurrencyItem("JPY", "¥", "Japanese Yen", "Japan", "🇯🇵", "Yen/Yuan"),
        CurrencyItem("CNY", "¥", "Chinese Yuan", "China", "🇨🇳", "Yen/Yuan"),
        CurrencyItem("KRW", "₩", "South Korean Won", "South Korea", "🇰🇷", "Other"),
        CurrencyItem("CHF", "CHF", "Swiss Franc", "Switzerland", "🇨🇭", "Other"),
        CurrencyItem("TRY", "₺", "Turkish Lira", "Turkey", "🇹🇷", "Other"),
        CurrencyItem("BRL", "R$", "Brazilian Real", "Brazil", "🇧🇷", "Other"),
        CurrencyItem("RUB", "₽", "Russian Ruble", "Russia", "🇷🇺", "Other"),
        CurrencyItem("ZAR", "R", "South African Rand", "South Africa", "🇿🇦", "Other"),
        CurrencyItem("SEK", "kr", "Swedish Krona", "Sweden", "🇸🇪", "Other"),
        CurrencyItem("NOK", "kr", "Norwegian Krone", "Norway", "🇳🇴", "Other"),
        CurrencyItem("DKK", "kr", "Danish Krone", "Denmark", "🇩🇰", "Other"),
        CurrencyItem("PLN", "zł", "Polish Złoty", "Poland", "🇵🇱", "Other"),
        CurrencyItem("NGN", "₦", "Nigerian Naira", "Nigeria", "🇳🇬", "Other"),
        CurrencyItem("MYR", "RM", "Malaysian Ringgit", "Malaysia", "🇲🇾", "Other"),
        CurrencyItem("IDR", "Rp", "Indonesian Rupiah", "Indonesia", "🇮🇩", "Other"),
        CurrencyItem("THB", "฿", "Thai Baht", "Thailand", "🇹🇭", "Other"),
        CurrencyItem("PHP", "₱", "Philippine Peso", "Philippines", "🇵🇭", "Other"),
        CurrencyItem("VND", "₫", "Vietnamese Dong", "Vietnam", "🇻🇳", "Other"),
        CurrencyItem("MXN", "Mex$", "Mexican Peso", "Mexico", "🇲🇽", "Dollar")
    )

    private var sharedPreferences: SharedPreferences? = null
    private val _selectedCurrency = MutableStateFlow(defaultCurrency)
    val selectedCurrency: StateFlow<CurrencyItem> = _selectedCurrency.asStateFlow()

    fun init(context: Context) {
        if (sharedPreferences == null) {
            sharedPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val localeDefault = try {
                val country = Locale.getDefault().country
                if (country.equals("IN", ignoreCase = true)) {
                    supportedCurrencies.find { it.code == "INR" } ?: defaultCurrency
                } else {
                    val code = java.util.Currency.getInstance(Locale.getDefault())?.currencyCode
                    supportedCurrencies.find { it.code.equals(code, ignoreCase = true) } ?: defaultCurrency
                }
            } catch (_: Exception) {
                defaultCurrency
            }
            val savedCode = sharedPreferences?.getString(KEY_CURRENCY_CODE, localeDefault.code) ?: localeDefault.code
            val found = supportedCurrencies.find { it.code.equals(savedCode, ignoreCase = true) } ?: localeDefault
            _selectedCurrency.value = found
        }
    }

    fun hasUserSetCurrency(userKey: String = ""): Boolean {
        if (userKey.isNotBlank()) {
            return sharedPreferences?.contains("${KEY_CURRENCY_CODE}_$userKey") == true ||
                    sharedPreferences?.contains(KEY_CURRENCY_CODE) == true
        }
        return sharedPreferences?.contains(KEY_CURRENCY_CODE) == true
    }

    fun loadCurrencyForUser(userKey: String = "guest") {
        val userSpecificCode = sharedPreferences?.getString("${KEY_CURRENCY_CODE}_$userKey", null)
        val fallbackCode = sharedPreferences?.getString(KEY_CURRENCY_CODE, defaultCurrency.code) ?: defaultCurrency.code
        val targetCode = userSpecificCode ?: fallbackCode
        val found = supportedCurrencies.find { it.code.equals(targetCode, ignoreCase = true) } ?: defaultCurrency
        _selectedCurrency.value = found
    }

    fun setCurrencyByCode(code: String, userKey: String = "") {
        val found = supportedCurrencies.find { it.code.equals(code, ignoreCase = true) }
        if (found != null) {
            setCurrency(found, userKey)
        }
    }

    fun setCurrency(currency: CurrencyItem, userKey: String = "") {
        _selectedCurrency.value = currency
        val editor = sharedPreferences?.edit()?.putString(KEY_CURRENCY_CODE, currency.code)
        if (userKey.isNotBlank()) {
            editor?.putString("${KEY_CURRENCY_CODE}_$userKey", currency.code)
        }
        editor?.apply()

        if (userKey.isNotBlank() && userKey != "guest" && com.waleed.vaulta.android.data.auth.supabase.SupabaseClientProvider.isConfigured()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val client = com.waleed.vaulta.android.data.auth.supabase.SupabaseClientProvider.client
                    client.from("profiles").update(
                        mapOf("currency_code" to currency.code)
                    ) {
                        filter {
                            eq("id", userKey)
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun getSelectedCurrency(): CurrencyItem {
        return _selectedCurrency.value
    }

    fun getSelectedCurrencySymbol(): String {
        return _selectedCurrency.value.symbol
    }

    fun formatAmount(amount: Double, currency: CurrencyItem = getSelectedCurrency()): String {
        val isNegative = amount < 0
        val absAmount = kotlin.math.abs(amount)
        val formattedNumber = String.format(Locale.US, "%,.2f", absAmount)
        val symbol = currency.symbol
        val hasLetters = symbol.any { it.isLetter() }
        val formattedWithSymbol = if (hasLetters) "$symbol $formattedNumber" else "$symbol$formattedNumber"
        return if (isNegative) "-$formattedWithSymbol" else formattedWithSymbol
    }
}
