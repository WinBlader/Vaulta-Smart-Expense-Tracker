package com.waleed.vaulta.android.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.waleed.vaulta.android.data.model.SharedSplitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SharedSplitDao {

    @Query("SELECT * FROM shared_splits ORDER BY timestamp DESC")
    fun getAllSharedSplits(): Flow<List<SharedSplitEntity>>

    @Query("""
        SELECT * FROM shared_splits 
        WHERE creatorId = :userKey 
           OR counterpartyId = :userKey 
           OR (:contact != '' AND (creatorContact = :contact OR counterpartyContact = :contact))
        ORDER BY timestamp DESC
    """)
    fun getSharedSplitsForUser(userKey: String, contact: String = ""): Flow<List<SharedSplitEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSharedSplit(split: SharedSplitEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSharedSplits(splits: List<SharedSplitEntity>)

    @Update
    suspend fun updateSharedSplit(split: SharedSplitEntity)

    @Delete
    suspend fun deleteSharedSplit(split: SharedSplitEntity)

    @Query("DELETE FROM shared_splits WHERE id = :id")
    suspend fun deleteSharedSplitById(id: String)

    @Query("UPDATE shared_splits SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("DELETE FROM shared_splits WHERE creatorId = :userKey OR counterpartyId = :userKey")
    suspend fun deleteSharedSplitsForUser(userKey: String)

    @Query("SELECT * FROM shared_splits WHERE creatorId = :userKey")
    suspend fun getSplitsCreatedByUser(userKey: String): List<SharedSplitEntity>

    @Query("SELECT * FROM shared_splits WHERE id = :id LIMIT 1")
    suspend fun getSharedSplitById(id: String): SharedSplitEntity?
}
