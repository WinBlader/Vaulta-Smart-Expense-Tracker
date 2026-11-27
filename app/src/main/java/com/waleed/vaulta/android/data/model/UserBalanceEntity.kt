package com.waleed.vaulta.android.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_balance")
data class UserBalanceEntity(
    @PrimaryKey
    val userId: String,
    val initialBalance: Double = 0.0,
    val currentBalance: Double = 0.0,
    val updatedAt: Long = System.currentTimeMillis()
)
