package com.cydoniancitizen.bingee.di

import com.cydoniancitizen.bingee.data.details.DefaultMediaDetailsRepository
import com.cydoniancitizen.bingee.data.tmdb.details.TmdbDetailsClient
import com.cydoniancitizen.bingee.data.tmdb.details.TmdbDetailsRemoteDataSource
import com.cydoniancitizen.bingee.domain.repository.MediaDetailsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DetailsModule {
    @Binds
    abstract fun bindMediaDetailsRepository(implementation: DefaultMediaDetailsRepository): MediaDetailsRepository

    @Binds
    abstract fun bindTmdbDetailsRemoteDataSource(implementation: TmdbDetailsClient): TmdbDetailsRemoteDataSource
}
