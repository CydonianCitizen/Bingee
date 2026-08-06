package com.cydoniancitizen.bingee.di

import com.cydoniancitizen.bingee.data.featured.DefaultFeaturedReleasesRepository
import com.cydoniancitizen.bingee.data.library.DefaultLibraryRepository
import com.cydoniancitizen.bingee.domain.repository.FeaturedReleasesRepository
import com.cydoniancitizen.bingee.domain.repository.LibraryRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class LibraryModule {
    @Binds
    abstract fun bindLibraryRepository(implementation: DefaultLibraryRepository): LibraryRepository

    @Binds
    abstract fun bindFeaturedReleasesRepository(
        implementation: DefaultFeaturedReleasesRepository
    ): FeaturedReleasesRepository
}
