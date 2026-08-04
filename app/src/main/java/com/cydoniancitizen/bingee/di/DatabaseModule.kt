package com.cydoniancitizen.bingee.di

import android.content.Context
import androidx.room.Room
import com.cydoniancitizen.bingee.core.model.ReleaseCalendarWindow
import com.cydoniancitizen.bingee.data.library.local.BingeeDatabase
import com.cydoniancitizen.bingee.data.library.local.DetailsDao
import com.cydoniancitizen.bingee.data.library.local.LibraryDao
import com.cydoniancitizen.bingee.data.library.local.MIGRATION_1_2
import com.cydoniancitizen.bingee.data.library.local.MIGRATION_2_3
import com.cydoniancitizen.bingee.data.library.local.MIGRATION_3_4
import com.cydoniancitizen.bingee.data.library.local.MIGRATION_4_5
import com.cydoniancitizen.bingee.data.library.local.MIGRATION_5_6
import com.cydoniancitizen.bingee.data.library.local.MIGRATION_6_7
import com.cydoniancitizen.bingee.data.library.local.NotificationDeliveryDao
import com.cydoniancitizen.bingee.data.library.local.PortableSnapshotDao
import com.cydoniancitizen.bingee.data.library.local.RatingDao
import com.cydoniancitizen.bingee.data.library.local.ReleaseEventDao
import com.cydoniancitizen.bingee.data.library.local.SeasonSummaryStore
import com.cydoniancitizen.bingee.data.library.local.SeriesDao
import com.cydoniancitizen.bingee.data.library.local.WatchProgressDao
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
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7
            )
            .build()

    @Provides
    fun provideLibraryDao(database: BingeeDatabase): LibraryDao = database.libraryDao()

    @Provides
    fun provideDetailsDao(database: BingeeDatabase): DetailsDao = database.detailsDao()

    @Provides
    fun provideSeriesDao(database: BingeeDatabase): SeriesDao = database.seriesDao()

    @Provides
    fun provideSeasonSummaryStore(seriesDao: SeriesDao): SeasonSummaryStore = seriesDao

    @Provides
    fun provideWatchProgressDao(database: BingeeDatabase): WatchProgressDao = database.watchProgressDao()

    @Provides
    fun provideRatingDao(database: BingeeDatabase): RatingDao = database.ratingDao()

    @Provides
    fun provideReleaseEventDao(database: BingeeDatabase): ReleaseEventDao = database.releaseEventDao()

    @Provides
    fun provideNotificationDeliveryDao(database: BingeeDatabase): NotificationDeliveryDao =
        database.notificationDeliveryDao()

    @Provides
    fun providePortableSnapshotDao(database: BingeeDatabase): PortableSnapshotDao = database.portableSnapshotDao()

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemUTC()

    @Provides
    @Singleton
    fun provideReleaseCalendarWindow(): ReleaseCalendarWindow = ReleaseCalendarWindow()
}
