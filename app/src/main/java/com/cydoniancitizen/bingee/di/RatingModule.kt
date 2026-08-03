package com.cydoniancitizen.bingee.di

import com.cydoniancitizen.bingee.data.rating.DefaultRatingRepository
import com.cydoniancitizen.bingee.domain.repository.RatingRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class RatingModule {
    @Binds
    abstract fun bindRatingRepository(implementation: DefaultRatingRepository): RatingRepository
}
