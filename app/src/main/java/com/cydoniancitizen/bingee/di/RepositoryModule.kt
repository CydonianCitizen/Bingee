package com.cydoniancitizen.bingee.di

import com.cydoniancitizen.bingee.data.calendar.DefaultReleaseCalendarRepository
import com.cydoniancitizen.bingee.data.calendar.MetadataCalendarStore
import com.cydoniancitizen.bingee.data.calendar.RoomMetadataCalendarStore
import com.cydoniancitizen.bingee.data.details.DefaultMediaDetailsRepository
import com.cydoniancitizen.bingee.data.featured.DefaultFeaturedReleasesRepository
import com.cydoniancitizen.bingee.data.imports.tvtime.AndroidTvTimeZipGateway
import com.cydoniancitizen.bingee.data.imports.tvtime.DefaultTvTimeTmdbGateway
import com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeTmdbGateway
import com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeZipGateway
import com.cydoniancitizen.bingee.data.library.DefaultLibraryRepository
import com.cydoniancitizen.bingee.data.progress.DefaultWatchProgressRepository
import com.cydoniancitizen.bingee.data.rating.DefaultRatingRepository
import com.cydoniancitizen.bingee.data.series.DefaultSeriesRepository
import com.cydoniancitizen.bingee.data.tmdb.details.TmdbDetailsClient
import com.cydoniancitizen.bingee.data.tmdb.details.TmdbDetailsRemoteDataSource
import com.cydoniancitizen.bingee.data.tmdb.search.DefaultMediaRepository
import com.cydoniancitizen.bingee.data.tmdb.series.TmdbSeasonClient
import com.cydoniancitizen.bingee.data.tmdb.series.TmdbSeasonRemoteDataSource
import com.cydoniancitizen.bingee.domain.calendar.CalendarDateSource
import com.cydoniancitizen.bingee.domain.calendar.DefaultCalendarRefreshCoordinator
import com.cydoniancitizen.bingee.domain.calendar.SystemCalendarDateSource
import com.cydoniancitizen.bingee.domain.repository.CalendarRefreshCoordinator
import com.cydoniancitizen.bingee.domain.repository.FeaturedReleasesRepository
import com.cydoniancitizen.bingee.domain.repository.LibraryRepository
import com.cydoniancitizen.bingee.domain.repository.MediaDetailsRepository
import com.cydoniancitizen.bingee.domain.repository.MediaRepository
import com.cydoniancitizen.bingee.domain.repository.RatingRepository
import com.cydoniancitizen.bingee.domain.repository.ReleaseCalendarRepository
import com.cydoniancitizen.bingee.domain.repository.SeriesRepository
import com.cydoniancitizen.bingee.domain.repository.WatchProgressRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds each repository and data-source contract to its single production implementation. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class RepositoryModule {
    @Binds
    abstract fun bindMediaRepository(implementation: DefaultMediaRepository): MediaRepository

    @Binds
    abstract fun bindRatingRepository(implementation: DefaultRatingRepository): RatingRepository

    @Binds
    abstract fun bindMediaDetailsRepository(implementation: DefaultMediaDetailsRepository): MediaDetailsRepository

    @Binds
    abstract fun bindTmdbDetailsRemoteDataSource(implementation: TmdbDetailsClient): TmdbDetailsRemoteDataSource

    @Binds
    abstract fun bindTvTimeZipGateway(implementation: AndroidTvTimeZipGateway): TvTimeZipGateway

    @Binds
    abstract fun bindTvTimeTmdbGateway(implementation: DefaultTvTimeTmdbGateway): TvTimeTmdbGateway

    @Binds
    abstract fun bindLibraryRepository(implementation: DefaultLibraryRepository): LibraryRepository

    @Binds
    abstract fun bindFeaturedReleasesRepository(
        implementation: DefaultFeaturedReleasesRepository
    ): FeaturedReleasesRepository

    @Binds
    abstract fun bindSeriesRepository(implementation: DefaultSeriesRepository): SeriesRepository

    @Binds
    abstract fun bindWatchProgressRepository(implementation: DefaultWatchProgressRepository): WatchProgressRepository

    @Binds
    abstract fun bindTmdbSeasonRemoteDataSource(implementation: TmdbSeasonClient): TmdbSeasonRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindMetadataCalendarStore(implementation: RoomMetadataCalendarStore): MetadataCalendarStore

    @Binds
    @Singleton
    abstract fun bindReleaseCalendarRepository(
        implementation: DefaultReleaseCalendarRepository
    ): ReleaseCalendarRepository

    @Binds
    @Singleton
    abstract fun bindCalendarRefreshCoordinator(
        implementation: DefaultCalendarRefreshCoordinator
    ): CalendarRefreshCoordinator

    @Binds
    @Singleton
    abstract fun bindCalendarDateSource(implementation: SystemCalendarDateSource): CalendarDateSource
}
