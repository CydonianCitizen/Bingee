package com.cydoniancitizen.bingee.di

import com.cydoniancitizen.bingee.data.progress.DefaultWatchProgressRepository
import com.cydoniancitizen.bingee.data.series.DefaultSeriesRepository
import com.cydoniancitizen.bingee.data.tmdb.series.TmdbSeasonClient
import com.cydoniancitizen.bingee.data.tmdb.series.TmdbSeasonRemoteDataSource
import com.cydoniancitizen.bingee.domain.repository.SeriesRepository
import com.cydoniancitizen.bingee.domain.repository.WatchProgressRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SeriesModule {
    @Binds
    abstract fun bindSeriesRepository(implementation: DefaultSeriesRepository): SeriesRepository

    @Binds
    abstract fun bindWatchProgressRepository(implementation: DefaultWatchProgressRepository): WatchProgressRepository

    @Binds
    abstract fun bindTmdbSeasonRemoteDataSource(implementation: TmdbSeasonClient): TmdbSeasonRemoteDataSource
}
