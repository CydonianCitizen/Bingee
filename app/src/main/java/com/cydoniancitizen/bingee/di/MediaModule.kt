package com.cydoniancitizen.bingee.di

import com.cydoniancitizen.bingee.data.tmdb.search.DefaultMediaRepository
import com.cydoniancitizen.bingee.domain.repository.MediaRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class MediaModule {
    @Binds
    abstract fun bindMediaRepository(implementation: DefaultMediaRepository): MediaRepository
}
