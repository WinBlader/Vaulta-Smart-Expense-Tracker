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
    @SerialName("note") val note: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)
