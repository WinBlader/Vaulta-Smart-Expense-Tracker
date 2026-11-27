package com.waleed.vaulta.android.di

import android.content.Context
import com.waleed.vaulta.android.data.ExpenseDatabase
import com.waleed.vaulta.android.data.dao.ExpenseDao
import com.waleed.vaulta.android.data.dao.SharedSplitDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ExpenseDatabase {
        return ExpenseDatabase.getInstance(context)
    }

    @Provides
    fun provideExpenseDao(database: ExpenseDatabase): ExpenseDao {
        return database.expenseDao()
    }

    @Provides
    fun provideSharedSplitDao(database: ExpenseDatabase): SharedSplitDao {
        return database.sharedSplitDao()
    }
}
