package com.cydoniancitizen.bingee.di

import com.cydoniancitizen.bingee.core.common.AnimeFeatureAvailability
import com.cydoniancitizen.bingee.core.common.ProductionAnimeFeatureAvailability
import com.cydoniancitizen.bingee.data.jikan.details.DefaultAnimeDetailsRepository
import com.cydoniancitizen.bingee.data.jikan.progress.DefaultAnimeProgressRepository
import com.cydoniancitizen.bingee.data.jikan.search.DefaultAnimeRepository
import com.cydoniancitizen.bingee.domain.repository.AnimeDetailsRepository
import com.cydoniancitizen.bingee.domain.repository.AnimeProgressRepository
import com.cydoniancitizen.bingee.domain.repository.AnimeRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AnimeModule {
    @Binds
    abstract fun bindAnimeFeatureAvailability(
        implementation: ProductionAnimeFeatureAvailability
    ): AnimeFeatureAvailability

    @Binds
    abstract fun bindAnimeRepository(implementation: DefaultAnimeRepository): AnimeRepository

    @Binds
    abstract fun bindAnimeDetailsRepository(implementation: DefaultAnimeDetailsRepository): AnimeDetailsRepository

    @Binds
    abstract fun bindAnimeProgressRepository(implementation: DefaultAnimeProgressRepository): AnimeProgressRepository
}
