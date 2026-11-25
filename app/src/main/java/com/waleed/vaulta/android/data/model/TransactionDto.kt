package com.waleed.vaulta.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TransactionDto(
    @SerialName("id") val id: String? = null,
    @SerialName("local_id") val localId: Int? = null,
    @SerialName("user_id") val userId: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("amount") val amount: Double = 0.0,
    @SerialName("date") val date: String = "",
    @SerialName("type") val type: String = "Expense",
    @SerialName("category") val category: String? = null,
    @SerialName("payment_method") val paymentMethod: String? = null,
    @SerialName("note") val note: String? = null,
    @SerialName("credit_direction") val creditDirection: String? = null,
    @SerialName("counterparty_name") val counterpartyName: String? = null,
    @SerialName("timestamp") val timestamp: Long? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)
