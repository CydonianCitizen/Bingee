package com.cydoniancitizen.bingee.di

import com.cydoniancitizen.bingee.data.equivalence.RoomMediaEquivalenceCandidateRepository
import com.cydoniancitizen.bingee.domain.repository.MediaEquivalenceCandidateRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class EquivalenceModule {

    @Binds
    @Singleton
    abstract fun bindMediaEquivalenceCandidateRepository(
        impl: RoomMediaEquivalenceCandidateRepository
    ): MediaEquivalenceCandidateRepository
}
