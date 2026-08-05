package com.cydoniancitizen.bingee.data.notification

import android.content.Context
import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.core.model.ReleaseEvent
import com.cydoniancitizen.bingee.core.model.ReleaseEventType
import com.cydoniancitizen.bingee.domain.notification.ReleaseNotificationContent
import com.cydoniancitizen.bingee.domain.notification.ReleaseNotificationContentMapper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class AndroidReleaseNotificationContentMapper @Inject constructor(
    @param:ApplicationContext private val context: Context
) : ReleaseNotificationContentMapper {
    override fun map(event: ReleaseEvent, daysUntilEvent: Int): ReleaseNotificationContent {
        val relative = when (daysUntilEvent) {
            0 -> context.getString(R.string.notification_relative_today)
            1 -> context.getString(R.string.notification_relative_tomorrow)
            else -> context.resources.getQuantityString(
                R.plurals.notification_relative_days,
                daysUntilEvent,
                daysUntilEvent
            )
        }
        val body = when (event.subject.eventType) {
            ReleaseEventType.MOVIE_RELEASE ->
                context.getString(R.string.notification_movie_body, relative)
            ReleaseEventType.SEASON_PREMIERE ->
                context.getString(
                    R.string.notification_season_body,
                    requireNotNull(event.seasonNumber),
                    relative
                )
            ReleaseEventType.EPISODE_AIRING ->
                context.getString(
                    R.string.notification_episode_body,
                    requireNotNull(event.seasonNumber),
                    requireNotNull(event.episodeNumber),
                    relative,
                    event.subjectTitle ?: context.getString(R.string.home_episode_title_unknown)
                )
            ReleaseEventType.ANIME_PREMIERE ->
                error("Anime premiere notifications are unsupported")
        }
        return ReleaseNotificationContent(title = event.title, body = body)
    }
}
