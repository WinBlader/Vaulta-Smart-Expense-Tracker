package com.waleed.vaulta.android.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.waleed.vaulta.android.data.model.ExpenseEntity
import com.waleed.vaulta.android.data.model.ExpenseSummary
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    @Query("SELECT * FROM expense_table WHERE userId = :userId ORDER BY id DESC")
    fun getAllExpenseForUser(userId: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expense_table WHERE userId = :userId AND type = 'Expense' ORDER BY amount DESC LIMIT 5")
    fun getTopExpensesForUser(userId: String): Flow<List<ExpenseEntity>>

    @Query("SELECT type, date, SUM(amount) AS total_amount FROM expense_table WHERE userId = :userId AND type = :type GROUP BY type, date ORDER BY date")
    fun getAllExpenseByDateForUser(type: String = "Expense", userId: String): Flow<List<ExpenseSummary>>

    // Legacy fallback queries
    @Query("SELECT * FROM expense_table ORDER BY id DESC")
    fun getAllExpense(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expense_table WHERE type = 'Expense' ORDER BY amount DESC LIMIT 5")
    fun getTopExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT type, date, SUM(amount) AS total_amount FROM expense_table WHERE type = :type GROUP BY type, date ORDER BY date")
    fun getAllExpenseByDate(type: String = "Expense"): Flow<List<ExpenseSummary>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expenseEntity: ExpenseEntity)

    @Delete
    suspend fun deleteExpense(expenseEntity: ExpenseEntity)

    @Update
    suspend fun updateExpense(expenseEntity: ExpenseEntity)

    @Query("DELETE FROM expense_table WHERE userId = :userId")
    suspend fun deleteExpensesForUser(userId: String)

    @Query("DELETE FROM expense_table")
    suspend fun clearAllExpenses()

    @Query("UPDATE expense_table SET userId = :userId WHERE userId = '' OR userId IS NULL OR userId = 'guest'")
    suspend fun claimGuestExpenses(userId: String)
}
