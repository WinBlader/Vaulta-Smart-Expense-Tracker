package com.waleed.vaulta.android.data.auth

object PhoneNormalizationUtils {

    data class NormalizedPhoneResult(
        val fullE164: String,    // e.g. "+917889779012" or "+12025550143"
        val loginEmail: String   // e.g. "7889779012@vaulta.app" or "12025550143@vaulta.app"
    )

    /**
     * Backward-compatible phone normalizer.
     * Strips all spaces, dashes, and non-digits.
     * - If country code is +91 and local number is 10 digits: maps to <10_local_digits>@vaulta.app
     * - For all other country codes (+1, +44, +971, etc.): maps to <country_code_digits><local_digits>@vaulta.app
     * - Full E.164 phone number is preserved as +<country_code_digits><local_digits>
     */
    fun normalizePhone(countryCode: String, localNumber: String): NormalizedPhoneResult {
        val cleanCountry = countryCode.filter { it.isDigit() }
        val cleanLocal = localNumber.filter { it.isDigit() }
        val fullE164 = "+$cleanCountry$cleanLocal"

        val loginEmail = if (cleanCountry == "91" && cleanLocal.length == 10) {
            "${cleanLocal}@vaulta.app"
        } else {
            "${cleanCountry}${cleanLocal}@vaulta.app"
        }

        return NormalizedPhoneResult(
            fullE164 = fullE164,
            loginEmail = loginEmail
        )
    }

    /**
     * Validates country code and local mobile number based on E.164 standards.
     * Returns null if valid, or a descriptive validation error string if invalid.
     */
    fun validatePhone(countryCode: String, localNumber: String): String? {
        val trimmedCode = countryCode.trim()
        if (!trimmedCode.startsWith("+") || trimmedCode.length < 2 || trimmedCode.length > 5) {
            return "Country code must start with '+' followed by 1 to 4 digits (e.g. +91, +1)."
        }
        val codeDigits = trimmedCode.drop(1)
        if (!codeDigits.all { it.isDigit() }) {
            return "Country code must contain digits only after '+'."
        }

        val localDigits = localNumber.filter { it.isDigit() }
        if (localDigits.length < 6 || localDigits.length > 14) {
            return "Mobile number must be between 6 and 14 digits."
        }

        val totalDigits = codeDigits.length + localDigits.length
        if (totalDigits !in 7..15) {
            return "Total phone number length must be between 7 and 15 digits (E.164 standard)."
        }

        return null
    }

    /**
     * Sanitizes user typed input in Country Code field, ensuring it starts with '+' and has up to 4 digits.
     */
    fun sanitizeCountryCodeInput(input: String): String {
        return if (input.startsWith("+")) {
            "+" + input.drop(1).filter { it.isDigit() }.take(4)
        } else {
            "+" + input.filter { it.isDigit() }.take(4)
        }
    }
}
