package com.waleed.vaulta.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RemoteTransactionDto(
    @SerialName("id") val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("title") val title: String,
    @SerialName("amount") val amount: Double,
    @SerialName("type") val type: String,
    @SerialName("category") val category: String = "General",
    @SerialName("payment_method") val paymentMethod: String? = null,
    @SerialName("note") val note: String? = null,
    @SerialName("date_millis") val dateMillis: Long,
    @SerialName("is_credit_owe") val isCreditOwe: Boolean = false,
    @SerialName("credit_owe_status") val creditOweStatus: String? = null
)
