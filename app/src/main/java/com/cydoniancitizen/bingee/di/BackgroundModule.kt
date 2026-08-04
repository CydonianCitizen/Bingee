package com.cydoniancitizen.bingee.di

import com.cydoniancitizen.bingee.data.background.WorkManagerBackgroundWorkScheduler
import com.cydoniancitizen.bingee.data.calendar.RoomBackgroundRefreshPlanner
import com.cydoniancitizen.bingee.data.notification.AndroidReleaseNotificationCapability
import com.cydoniancitizen.bingee.data.notification.AndroidReleaseNotificationContentMapper
import com.cydoniancitizen.bingee.data.notification.AndroidReleaseNotifier
import com.cydoniancitizen.bingee.data.notification.DefaultNotificationDispatchCoordinator
import com.cydoniancitizen.bingee.data.notification.RoomNotificationDeliveryRepository
import com.cydoniancitizen.bingee.data.settings.DataStoreReleaseNotificationPreferences
import com.cydoniancitizen.bingee.domain.background.BackgroundWorkScheduler
import com.cydoniancitizen.bingee.domain.notification.NotificationDispatchCoordinator
import com.cydoniancitizen.bingee.domain.notification.ReleaseNotificationCapability
import com.cydoniancitizen.bingee.domain.notification.ReleaseNotificationContentMapper
import com.cydoniancitizen.bingee.domain.notification.ReleaseNotifier
import com.cydoniancitizen.bingee.domain.repository.BackgroundRefreshPlanner
import com.cydoniancitizen.bingee.domain.repository.NotificationDeliveryRepository
import com.cydoniancitizen.bingee.domain.repository.ReleaseNotificationPreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class BackgroundModule {
    @Binds
    abstract fun bindScheduler(implementation: WorkManagerBackgroundWorkScheduler): BackgroundWorkScheduler

    @Binds
    abstract fun bindPlanner(implementation: RoomBackgroundRefreshPlanner): BackgroundRefreshPlanner

    @Binds
    abstract fun bindPreferences(
        implementation: DataStoreReleaseNotificationPreferences
    ): ReleaseNotificationPreferencesRepository

    @Binds
    abstract fun bindCapability(implementation: AndroidReleaseNotificationCapability): ReleaseNotificationCapability

    @Binds
    abstract fun bindContentMapper(
        implementation: AndroidReleaseNotificationContentMapper
    ): ReleaseNotificationContentMapper

    @Binds
    abstract fun bindNotifier(implementation: AndroidReleaseNotifier): ReleaseNotifier

    @Binds
    abstract fun bindDeliveryRepository(
        implementation: RoomNotificationDeliveryRepository
    ): NotificationDeliveryRepository

    @Binds
    abstract fun bindDispatchCoordinator(
        implementation: DefaultNotificationDispatchCoordinator
    ): NotificationDispatchCoordinator
}
