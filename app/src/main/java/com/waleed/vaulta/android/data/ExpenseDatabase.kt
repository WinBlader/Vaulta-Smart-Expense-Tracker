package com.waleed.vaulta.android.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.waleed.vaulta.android.data.dao.ExpenseDao
import com.waleed.vaulta.android.data.dao.CreditOweDao
import com.waleed.vaulta.android.data.dao.SharedSplitDao
import com.waleed.vaulta.android.data.dao.UserBalanceDao
import com.waleed.vaulta.android.data.model.CreditOweEntity
import com.waleed.vaulta.android.data.model.ExpenseEntity
import com.waleed.vaulta.android.data.model.SharedSplitEntity
import com.waleed.vaulta.android.data.model.UserBalanceEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton

@Database(entities = [ExpenseEntity::class, SharedSplitEntity::class, UserBalanceEntity::class, CreditOweEntity::class], version = 8, exportSchema = false)
@Singleton
abstract class ExpenseDatabase : RoomDatabase() {

    abstract fun expenseDao(): ExpenseDao
    abstract fun sharedSplitDao(): SharedSplitDao
    abstract fun userBalanceDao(): UserBalanceDao
    abstract fun creditOweDao(): CreditOweDao

    companion object {
        const val DATABASE_NAME = "expense_database"

        @Volatile
        private var INSTANCE: ExpenseDatabase? = null

        fun getInstance(@ApplicationContext context: Context): ExpenseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ExpenseDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS expense_table_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                amount REAL NOT NULL,
                date TEXT NOT NULL,
                type TEXT NOT NULL
            )
            """.trimIndent()
        )

        // Step 2: Copy the data from the old table to the new table
        database.execSQL(
            """
            INSERT INTO expense_table_new (id, title, amount, date, type)
            SELECT id, title, amount, date, type FROM expense_table
            """.trimIndent()
        )

        // Step 3: Drop the old table
        database.execSQL("DROP TABLE expense_table")

        // Step 4: Rename the new table to the original table name
        database.execSQL("ALTER TABLE expense_table_new RENAME TO expense_table")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE expense_table ADD COLUMN userId TEXT NOT NULL DEFAULT 'guest'")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE expense_table ADD COLUMN note TEXT DEFAULT NULL")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE expense_table ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS shared_splits (
                id TEXT NOT NULL PRIMARY KEY,
                groupId TEXT,
                title TEXT NOT NULL,
                totalAmount REAL NOT NULL,
                splitAmount REAL NOT NULL,
                creatorId TEXT NOT NULL,
                creatorName TEXT NOT NULL,
                creatorContact TEXT,
                counterpartyId TEXT,
                counterpartyName TEXT NOT NULL,
                counterpartyContact TEXT,
                direction TEXT NOT NULL,
                status TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                notes TEXT
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS user_balance (
                userId TEXT NOT NULL PRIMARY KEY,
                initialBalance REAL NOT NULL DEFAULT 0.0,
                currentBalance REAL NOT NULL DEFAULT 0.0,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS credit_owe (
                id TEXT NOT NULL PRIMARY KEY,
                user_id TEXT NOT NULL,
                counterparty_id TEXT,
                person_name TEXT NOT NULL,
                counterparty_contact TEXT,
                type TEXT NOT NULL,
                amount REAL NOT NULL,
                note TEXT,
                status TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                settled_at INTEGER
            )
            """.trimIndent()
        )
        // Clean up legacy split bill creator expenses or credit/owe entries if any
        database.execSQL("DELETE FROM expense_table WHERE type IN ('Credit', 'Owe', 'CREDIT', 'OWE') OR note LIKE 'Split bill:%' OR title LIKE '%(My Share)'")
    }
}
