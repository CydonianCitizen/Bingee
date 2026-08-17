package com.cydoniancitizen.bingee.data.library.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        MediaEntity::class,
        ExternalRefEntity::class,
        LibraryMembershipEntity::class,
        MediaDetailsEntity::class,
        MediaGenreEntity::class,
        SeasonEntity::class,
        EpisodeEntity::class,
        EpisodeWatchProgressEntity::class,
        MovieWatchProgressEntity::class,
        SeriesWatchProgressEntity::class,
        MediaRatingEntity::class,
        ReleaseEventEntity::class,
        CalendarRefreshStateEntity::class,
        NotificationDeliveryEntity::class,
        PortablePreferencesEntity::class,
        ImportProvenanceRefEntity::class,
        SeriesStateOverrideEntity::class
    ],
    version = 4,
    exportSchema = true
)
@TypeConverters(RoomConverters::class)
internal abstract class BingeeDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao

    abstract fun detailsDao(): DetailsDao

    abstract fun seriesDao(): SeriesDao

    abstract fun watchProgressDao(): WatchProgressDao

    abstract fun importProgressDao(): ImportProgressDao

    abstract fun ratingDao(): RatingDao

    abstract fun releaseEventDao(): ReleaseEventDao

    abstract fun notificationDeliveryDao(): NotificationDeliveryDao

    abstract fun portableSnapshotDao(): PortableSnapshotDao

    abstract fun importProvenanceDao(): ImportProvenanceDao

    companion object {
        const val DATABASE_NAME = "bingee.db"
    }
}
