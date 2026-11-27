package com.waleed.vaulta.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreditOweDto(
    @SerialName("id")
    val id: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("counterparty_id")
    val counterpartyId: String? = null,
    @SerialName("person_name")
    val personName: String,
    @SerialName("counterparty_contact")
    val counterpartyContact: String? = null,
    @SerialName("type")
    val type: String,
    @SerialName("amount")
    val amount: Double,
    @SerialName("note")
    val note: String? = null,
    @SerialName("status")
    val status: String = "PENDING",
    @SerialName("created_at")
    val createdAt: Long,
    @SerialName("settled_at")
    val settledAt: Long? = null
)

fun CreditOweEntity.toDto(): CreditOweDto = CreditOweDto(
    id = id,
    userId = userId,
    counterpartyId = counterpartyId,
    personName = personName,
    counterpartyContact = counterpartyContact,
    type = type,
    amount = amount,
    note = note,
    status = status,
    createdAt = createdAt,
    settledAt = settledAt
)

fun CreditOweDto.toEntity(): CreditOweEntity = CreditOweEntity(
    id = id,
    userId = userId,
    counterpartyId = counterpartyId,
    personName = personName,
    counterpartyContact = counterpartyContact,
    type = type,
    amount = amount,
    note = note,
    status = status,
    createdAt = createdAt,
    settledAt = settledAt
)

