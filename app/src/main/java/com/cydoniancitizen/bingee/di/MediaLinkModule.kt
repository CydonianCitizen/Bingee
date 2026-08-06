package com.cydoniancitizen.bingee.di

import com.cydoniancitizen.bingee.data.link.GroupUuidGenerator
import com.cydoniancitizen.bingee.data.link.RandomGroupUuidGenerator
import com.cydoniancitizen.bingee.data.link.RoomMediaLinkRepository
import com.cydoniancitizen.bingee.domain.repository.MediaLinkRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class MediaLinkModule {

    @Binds
    @Singleton
    abstract fun bindMediaLinkRepository(repository: RoomMediaLinkRepository): MediaLinkRepository

    companion object {
        @Provides
        @Singleton
        fun provideGroupUuidGenerator(generator: RandomGroupUuidGenerator): GroupUuidGenerator = generator
    }
}
