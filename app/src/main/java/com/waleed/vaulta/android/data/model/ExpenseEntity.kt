package com.waleed.vaulta.android.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "expense_table")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Int? = null,
    val title: String,
    val amount: Double,
    val date: String,
    val type: String,
    val userId: String = "guest",
    val note: String? = null,
    val uuid: String = UUID.randomUUID().toString()
)
