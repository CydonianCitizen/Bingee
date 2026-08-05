package com.cydoniancitizen.bingee.data.calendar

import com.cydoniancitizen.bingee.core.model.AnimeDetails
import com.cydoniancitizen.bingee.core.model.Episode
import com.cydoniancitizen.bingee.core.model.MediaDetails
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.ReleaseEventType
import com.cydoniancitizen.bingee.core.model.ReleaseSubjectIdentity
import com.cydoniancitizen.bingee.core.model.ReleaseSubjectType
import com.cydoniancitizen.bingee.core.model.Season
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

internal data class ProjectedReleaseEvent(
    val identity: ReleaseSubjectIdentity,
    val eventDate: LocalDate,
    val projectedAt: Instant,
    val sourceMetadataUpdatedAt: Instant
)

internal class ReleaseEventProjector @Inject constructor() {
    fun movie(details: MediaDetails, updatedAt: Instant): ProjectedReleaseEvent? {
        require(details.mediaType == MediaType.MOVIE) { "Movie release projection requires a movie" }
        return details.releaseDate?.let { date ->
            ProjectedReleaseEvent(
                identity = ReleaseSubjectIdentity(
                    source = details.externalRef.source,
                    subjectType = ReleaseSubjectType.MEDIA,
                    externalId = details.externalRef.externalId,
                    eventType = ReleaseEventType.MOVIE_RELEASE
                ),
                eventDate = date,
                projectedAt = updatedAt,
                sourceMetadataUpdatedAt = updatedAt
            )
        }
    }

    fun season(season: Season, updatedAt: Instant): ProjectedReleaseEvent? = season.airDate?.let { date ->
        ProjectedReleaseEvent(
            identity = ReleaseSubjectIdentity(
                source = season.externalRef.source,
                subjectType = ReleaseSubjectType.SEASON,
                externalId = season.externalRef.externalId,
                eventType = ReleaseEventType.SEASON_PREMIERE
            ),
            eventDate = date,
            projectedAt = updatedAt,
            sourceMetadataUpdatedAt = updatedAt
        )
    }

    fun episode(episode: Episode, updatedAt: Instant): ProjectedReleaseEvent? = episode.airDate?.let { date ->
        ProjectedReleaseEvent(
            identity = ReleaseSubjectIdentity(
                source = episode.externalRef.source,
                subjectType = ReleaseSubjectType.EPISODE,
                externalId = episode.externalRef.externalId,
                eventType = ReleaseEventType.EPISODE_AIRING
            ),
            eventDate = date,
            projectedAt = updatedAt,
            sourceMetadataUpdatedAt = updatedAt
        )
    }

    fun anime(details: AnimeDetails, updatedAt: Instant): ProjectedReleaseEvent? = details.startDate?.let { date ->
        ProjectedReleaseEvent(
            identity = ReleaseSubjectIdentity(
                source = details.externalRef.source,
                subjectType = ReleaseSubjectType.MEDIA,
                externalId = details.externalRef.externalId,
                eventType = ReleaseEventType.ANIME_PREMIERE
            ),
            eventDate = date,
            projectedAt = updatedAt,
            sourceMetadataUpdatedAt = updatedAt
        )
    }
}
