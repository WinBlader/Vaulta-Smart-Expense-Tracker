package com.waleed.vaulta.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SharedSplitDto(
    @SerialName("id")
    val id: String,
    @SerialName("group_id")
    val groupId: String? = null,
    @SerialName("title")
    val title: String,
    @SerialName("total_amount")
    val totalAmount: Double,
    @SerialName("split_amount")
    val splitAmount: Double,
    @SerialName("creator_id")
    val creatorId: String,
    @SerialName("creator_name")
    val creatorName: String,
    @SerialName("creator_contact")
    val creatorContact: String? = null,
    @SerialName("counterparty_id")
    val counterpartyId: String? = null,
    @SerialName("counterparty_name")
    val counterpartyName: String,
    @SerialName("counterparty_contact")
    val counterpartyContact: String? = null,
    @SerialName("direction")
    val direction: String,
    @SerialName("status")
    val status: String = "PENDING",
    @SerialName("timestamp")
    val timestamp: Long,
    @SerialName("notes")
    val notes: String? = null
)
