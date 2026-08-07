package com.cydoniancitizen.bingee.di

import android.content.Context
import androidx.room.Room
import com.cydoniancitizen.bingee.core.model.ReleaseCalendarWindow
import com.cydoniancitizen.bingee.data.library.local.BingeeDatabase
import com.cydoniancitizen.bingee.data.library.local.DetailsDao
import com.cydoniancitizen.bingee.data.library.local.ImportProgressDao
import com.cydoniancitizen.bingee.data.library.local.ImportProvenanceDao
import com.cydoniancitizen.bingee.data.library.local.LibraryDao
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
    fun provideImportProgressDao(database: BingeeDatabase): ImportProgressDao = database.importProgressDao()

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
    fun provideImportProvenanceDao(database: BingeeDatabase): ImportProvenanceDao = database.importProvenanceDao()

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemUTC()

    @Provides
    @Singleton
    fun provideReleaseCalendarWindow(): ReleaseCalendarWindow = ReleaseCalendarWindow()
}
