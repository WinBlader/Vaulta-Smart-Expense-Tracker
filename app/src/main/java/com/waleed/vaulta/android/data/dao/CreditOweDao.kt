package com.waleed.vaulta.android.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.waleed.vaulta.android.data.model.CreditOweEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CreditOweDao {

    @Query("""
        SELECT * FROM credit_owe 
        WHERE user_id = :userId 
           OR counterparty_id = :userId 
           OR (:contact != '' AND counterparty_contact = :contact)
        ORDER BY created_at DESC
    """)
    fun getCreditOweForUser(userId: String, contact: String): Flow<List<CreditOweEntity>>

    @Query("SELECT * FROM credit_owe WHERE user_id = :userId ORDER BY created_at DESC")
    suspend fun getCreditOweCreatedByUser(userId: String): List<CreditOweEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCreditOwe(entry: CreditOweEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCreditOweList(entries: List<CreditOweEntity>)

    @Update
    suspend fun updateCreditOwe(entry: CreditOweEntity)

    @Query("UPDATE credit_owe SET status = :status, settled_at = :settledAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, settledAt: Long?)

    @Query("DELETE FROM credit_owe WHERE id = :id")
    suspend fun deleteCreditOweById(id: String)

    @Query("DELETE FROM credit_owe WHERE user_id = :userId")
    suspend fun deleteCreditOweForUser(userId: String)
}

