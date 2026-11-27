package com.waleed.vaulta.android.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "shared_splits")
data class SharedSplitEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val groupId: String? = null,
    val title: String,
    val totalAmount: Double,
    val splitAmount: Double,
    val creatorId: String,
    val creatorName: String,
    val creatorContact: String? = null,
    val counterpartyId: String? = null,
    val counterpartyName: String,
    val counterpartyContact: String? = null,
    val direction: String, // "OWED_TO_CREATOR" or "CREATOR_OWES"
    val status: String = "PENDING", // "PENDING" or "SETTLED"
    val timestamp: Long = System.currentTimeMillis(),
    val notes: String? = null
)
