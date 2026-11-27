package com.waleed.vaulta.android.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.waleed.vaulta.android.data.model.UserBalanceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserBalanceDao {

    @Query("SELECT * FROM user_balance WHERE userId = :userId LIMIT 1")
    fun observeBalance(userId: String): Flow<UserBalanceEntity?>

    @Query("SELECT * FROM user_balance WHERE userId = :userId LIMIT 1")
    suspend fun getBalance(userId: String): UserBalanceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: UserBalanceEntity)

    @Query("DELETE FROM user_balance")
    suspend fun clearAll()

    @Query("DELETE FROM user_balance WHERE userId = :userId")
    suspend fun deleteForUser(userId: String)
}
