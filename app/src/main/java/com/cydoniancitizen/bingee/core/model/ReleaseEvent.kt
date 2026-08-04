package com.cydoniancitizen.bingee.core.model

import java.time.LocalDate

enum class ReleaseSubjectType { MEDIA, SEASON, EPISODE }

enum class ReleaseEventType { MOVIE_RELEASE, SEASON_PREMIERE, EPISODE_AIRING }

data class ReleaseSubjectIdentity(
    val source: MediaSource,
    val subjectType: ReleaseSubjectType,
    val externalId: String,
    val eventType: ReleaseEventType
) {
    init {
        require(externalId.isNotBlank()) { "Release subject external ID must not be blank" }
    }

    val stableKey: String
        get() = "${source.name}:${subjectType.name}:$externalId:${eventType.name}"
}

data class ReleaseEvent(
    val mediaRef: ExternalMediaRef,
    val subject: ReleaseSubjectIdentity,
    val mediaType: MediaType,
    val eventDate: LocalDate,
    val title: String,
    val posterUrl: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val subjectTitle: String? = null
) {
    init {
        require(mediaRef.source == subject.source) { "Release event parent and subject providers must match" }
        require(title.isNotBlank()) { "Release event title must not be blank" }
        require(seasonNumber == null || seasonNumber >= 0) { "Season number must not be negative" }
        require(episodeNumber == null || episodeNumber > 0) { "Episode number must be positive" }
        require(
            when (subject.eventType) {
                ReleaseEventType.MOVIE_RELEASE ->
                    subject.subjectType == ReleaseSubjectType.MEDIA && mediaType == MediaType.MOVIE &&
                        seasonNumber == null && episodeNumber == null
                ReleaseEventType.SEASON_PREMIERE ->
                    subject.subjectType == ReleaseSubjectType.SEASON && mediaType == MediaType.SERIES &&
                        seasonNumber != null && episodeNumber == null
                ReleaseEventType.EPISODE_AIRING ->
                    subject.subjectType == ReleaseSubjectType.EPISODE && mediaType == MediaType.SERIES &&
                        seasonNumber != null && episodeNumber != null
            }
        ) { "Release event type does not match its subject" }
    }

    val stableKey: String get() = subject.stableKey
}
