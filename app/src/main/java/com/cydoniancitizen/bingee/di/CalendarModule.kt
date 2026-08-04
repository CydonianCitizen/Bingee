package com.cydoniancitizen.bingee.di

import com.cydoniancitizen.bingee.data.calendar.DefaultReleaseCalendarRepository
import com.cydoniancitizen.bingee.data.calendar.MetadataCalendarStore
import com.cydoniancitizen.bingee.data.calendar.RoomMetadataCalendarStore
import com.cydoniancitizen.bingee.domain.calendar.CalendarDateSource
import com.cydoniancitizen.bingee.domain.calendar.DefaultCalendarRefreshCoordinator
import com.cydoniancitizen.bingee.domain.calendar.SystemCalendarDateSource
import com.cydoniancitizen.bingee.domain.repository.CalendarRefreshCoordinator
import com.cydoniancitizen.bingee.domain.repository.ReleaseCalendarRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class CalendarModule {
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
