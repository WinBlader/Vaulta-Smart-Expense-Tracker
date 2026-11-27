package com.waleed.vaulta.android.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "credit_owe")
data class CreditOweEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "counterparty_id")
    val counterpartyId: String? = null,
    @ColumnInfo(name = "person_name")
    val personName: String,
    @ColumnInfo(name = "counterparty_contact")
    val counterpartyContact: String? = null,
    val type: String, // "CREDIT" (they owe you / owed to you) or "OWE" (you owe them)
    val amount: Double,
    val note: String? = null,
    val status: String = "PENDING", // "PENDING" or "SETTLED"
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "settled_at")
    val settledAt: Long? = null
)

