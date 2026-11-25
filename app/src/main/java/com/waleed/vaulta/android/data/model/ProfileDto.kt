package com.waleed.vaulta.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileDto(
    @SerialName("id") val id: String? = null,
    @SerialName("username") val username: String? = null,
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("email") val email: String? = null,
    @SerialName("phone_number") val phoneNumber: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("auth_provider") val authProvider: String? = "Email",
    @SerialName("monthly_budget") val monthlyBudget: Double? = 0.0,
    @SerialName("currency_code") val currencyCode: String? = "INR",
    @SerialName("privacy_policy_accepted") val privacyPolicyAccepted: Boolean? = true,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
) {
    val cleanPhoneNumber: String
        get() = if (phoneNumber.isNullOrBlank() || phoneNumber.equals("EMPTY", ignoreCase = true)) "" else phoneNumber
}
