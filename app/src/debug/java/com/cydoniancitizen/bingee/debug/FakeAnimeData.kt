package com.cydoniancitizen.bingee.debug

import com.cydoniancitizen.bingee.core.model.AnimeCompletionOrigin
import com.cydoniancitizen.bingee.core.model.AnimeDetails
import com.cydoniancitizen.bingee.core.model.AnimeFormat
import com.cydoniancitizen.bingee.core.model.AnimeRelation
import com.cydoniancitizen.bingee.core.model.AnimeStatus
import com.cydoniancitizen.bingee.core.model.AnimeWatchProgress
import com.cydoniancitizen.bingee.core.model.CacheFreshness
import com.cydoniancitizen.bingee.core.model.CachedAnimeDetails
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.LibraryEntry
import com.cydoniancitizen.bingee.core.model.LibraryProgress
import com.cydoniancitizen.bingee.core.model.MediaSearchPage
import com.cydoniancitizen.bingee.core.model.MediaSearchResult
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.PersonalRating
import com.cydoniancitizen.bingee.core.model.ReleaseEvent
import com.cydoniancitizen.bingee.core.model.ReleaseEventType
import com.cydoniancitizen.bingee.core.model.ReleaseSubjectIdentity
import com.cydoniancitizen.bingee.core.model.ReleaseSubjectType
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import java.time.Instant
import java.time.LocalDate

/** Synthetic Anime-only data for debug previews and deterministic JVM tests. */
object FakeAnimeData {
    val fixedNow: Instant = FakeMediaData.fixedNow
    val animeRef = ExternalMediaRef(MediaSource.JIKAN, "52991")
    val collisionTmdbRef = ExternalMediaRef(MediaSource.TMDB, "550")
    val collisionJikanRef = ExternalMediaRef(MediaSource.JIKAN, "550")

    val longEnglishTitle =
        "The Deliberately Long English Anime Title Used For Accessible Layout Previewing"
    val longJapaneseTitle = "長い日本語アニメ題名で大きな文字表示と折り返しを検証するための合成作品"

    val searchResult = MediaSearchResult(
        externalRef = animeRef,
        mediaType = MediaType.ANIME,
        title = longEnglishTitle,
        originalTitle = longJapaneseTitle,
        releaseDate = LocalDate.of(2025, 1, 8)
    )

    val secondSearchResult = MediaSearchResult(
        externalRef = ExternalMediaRef(MediaSource.JIKAN, "52992"),
        mediaType = MediaType.ANIME,
        title = "Synthetic Anime Movie",
        originalTitle = "合成アニメ映画",
        releaseDate = LocalDate.of(2024, 7, 20)
    )

    val searchResults = listOf(searchResult, secondSearchResult)
    val searchPage = MediaSearchPage(searchResults, page = 1, totalPages = 1, totalResults = 2)
    val emptySearch = MediaSearchPage(emptyList(), page = 1, totalPages = 1, totalResults = 0)
    val networkUnavailable: AppResult<MediaSearchPage> = AppResult.Failure(AppError.NetworkUnavailable)
    val rateLimited: AppResult<MediaSearchPage> = AppResult.Failure(AppError.RateLimited)

    private val relatedEntries = listOf(
        AnimeRelation(
            relation = "Sequel",
            animeRef = ExternalMediaRef(MediaSource.JIKAN, "52992"),
            title = "Synthetic Anime Sequel",
            format = AnimeFormat.TV
        ),
        AnimeRelation(
            relation = "Prequel",
            animeRef = ExternalMediaRef(MediaSource.JIKAN, "52990"),
            title = "Synthetic Anime Movie",
            format = AnimeFormat.MOVIE
        )
    )

    private val completeDetails = AnimeDetails(
        externalRef = animeRef,
        title = longEnglishTitle,
        englishTitle = longEnglishTitle,
        japaneseTitle = longJapaneseTitle,
        synopsis = "Synthetic synopsis for offline preview and deterministic test state.",
        format = AnimeFormat.TV,
        status = AnimeStatus.FINISHED,
        episodeCount = 12,
        duration = "24 min per ep",
        startDate = LocalDate.of(2025, 1, 8),
        endDate = LocalDate.of(2025, 3, 26),
        season = "winter",
        year = 2025,
        providerScore = 8.7,
        relations = relatedEntries
    )

    val cachedDetails = CachedAnimeDetails(completeDetails, fixedNow, CacheFreshness.FRESH)
    val staleDetails = CachedAnimeDetails(
        completeDetails.copy(
            status = AnimeStatus.AIRING,
            episodeCount = null,
            endDate = null,
            providerScore = null
        ),
        fixedNow.minusSeconds(26 * 60 * 60),
        CacheFreshness.STALE
    )

    val missingOptionalFields = AnimeDetails(
        externalRef = ExternalMediaRef(MediaSource.JIKAN, "52993"),
        title = "Sparse Synthetic Anime"
    )
    val unknownFormat = completeDetails.copy(format = AnimeFormat.UNKNOWN)
    val unknownStatus = completeDetails.copy(status = AnimeStatus.UNKNOWN)
    val knownTotalDetails = completeDetails.copy(relations = emptyList())
    val unknownTotalDetails = completeDetails.copy(
        title = "Synthetic Anime With Unknown Episode Total",
        episodeCount = null,
        status = AnimeStatus.UNKNOWN,
        endDate = null
    )
    val ongoingAnime = completeDetails.copy(status = AnimeStatus.AIRING, endDate = null)
    val completedAnime = completeDetails.copy(status = AnimeStatus.FINISHED)
    val movieAnime = completeDetails.copy(
        title = "Synthetic Anime Movie",
        format = AnimeFormat.MOVIE,
        episodeCount = 1,
        status = AnimeStatus.FINISHED,
        relations = emptyList()
    )
    val relatedAnime = completeDetails

    val knownTotalProgress = AnimeWatchProgress(5, null, null, fixedNow)
    val unknownTotalProgress = AnimeWatchProgress(3, null, null, fixedNow)
    val completedProgress = AnimeWatchProgress(
        watchedEpisodes = 12,
        completedAt = fixedNow,
        completionOrigin = AnimeCompletionOrigin.EXPLICIT,
        updatedAt = fixedNow
    )
    val movieProgress = AnimeWatchProgress(
        watchedEpisodes = 1,
        completedAt = fixedNow,
        completionOrigin = AnimeCompletionOrigin.EXPLICIT,
        updatedAt = fixedNow
    )
    val localRating = PersonalRating(8)

    val mixedLibraryEntries = FakeMediaData.libraryEntries + listOf(
        LibraryEntry(
            mediaRef = collisionJikanRef,
            mediaType = MediaType.ANIME,
            title = longEnglishTitle,
            originalTitle = longJapaneseTitle,
            releaseDate = completeDetails.startDate,
            addedAt = fixedNow.minusSeconds(3_600),
            progress = LibraryProgress.Anime(5, 12, completed = false),
            personalRating = localRating
        ),
        LibraryEntry(
            mediaRef = ExternalMediaRef(MediaSource.JIKAN, "52992"),
            mediaType = MediaType.ANIME,
            title = "Synthetic Anime Movie",
            originalTitle = "合成アニメ映画",
            releaseDate = movieAnime.startDate,
            addedAt = fixedNow.minusSeconds(7_200),
            progress = LibraryProgress.Anime(1, 1, completed = true)
        )
    )

    val animePremiere = ReleaseEvent(
        mediaRef = animeRef,
        subject = ReleaseSubjectIdentity(
            source = MediaSource.JIKAN,
            subjectType = ReleaseSubjectType.MEDIA,
            externalId = animeRef.externalId,
            eventType = ReleaseEventType.ANIME_PREMIERE
        ),
        mediaType = MediaType.ANIME,
        eventDate = LocalDate.of(2026, 8, 20),
        title = longEnglishTitle
    )
}
