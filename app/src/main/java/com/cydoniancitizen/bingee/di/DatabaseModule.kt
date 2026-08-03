package com.cydoniancitizen.bingee.di

import android.content.Context
import androidx.room.Room
import com.cydoniancitizen.bingee.data.library.local.BingeeDatabase
import com.cydoniancitizen.bingee.data.library.local.DetailsDao
import com.cydoniancitizen.bingee.data.library.local.LibraryDao
import com.cydoniancitizen.bingee.data.library.local.MIGRATION_1_2
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BingeeDatabase =
        Room.databaseBuilder(context, BingeeDatabase::class.java, BingeeDatabase.DATABASE_NAME)
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun provideLibraryDao(database: BingeeDatabase): LibraryDao = database.libraryDao()

    @Provides
    fun provideDetailsDao(database: BingeeDatabase): DetailsDao = database.detailsDao()

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemUTC()
}
