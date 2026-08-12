@file:Suppress("ktlint:standard:max-line-length")

package com.cydoniancitizen.bingee.data.imports.tvtime

import com.cydoniancitizen.bingee.core.model.Episode
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaDetails
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.Season
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.imports.model.ImportSourceLocation
import com.cydoniancitizen.bingee.data.imports.model.ImportedEpisodeHint
import com.cydoniancitizen.bingee.data.imports.model.ImportedIdentityNamespace
import com.cydoniancitizen.bingee.data.imports.model.ImportedMediaHint
import com.cydoniancitizen.bingee.data.imports.model.ImportedSourceDocument
import com.cydoniancitizen.bingee.data.imports.model.ImportedSourceIdentity
import com.cydoniancitizen.bingee.data.imports.model.ImportedSourceSummary
import com.cydoniancitizen.bingee.data.imports.model.ImportedTimestamp
import com.cydoniancitizen.bingee.data.imports.model.ImportedUnsupportedFields
import com.cydoniancitizen.bingee.data.imports.model.ImportedWatchRecord
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TvTimeMatcherTest {
    @Test
    fun acceptsUniqueExactImdbMovie() = runTest {
        val candidate = movieCandidate("10", "Exact Movie", 2020)
        val gateway = FakeGateway().apply { media["IMDB:tt123"] = listOf(candidate) }
        val report = TvTimeMatcher(gateway).match(document(movies = listOf(movie("tt123"))))
        assertEquals(TvTimeMatchConfidence.EXACT, report.media.single().confidence)
        assertEquals(TvTimeReviewAction.ACCEPT_PROPOSED, report.media.single().action)
    }

    @Test
    fun acceptsMovieOnlyWhenTitleAndYearAreUnique() = runTest {
        val candidate = movieCandidate("10", "Example Movie", 2020)
        val gateway = FakeGateway().apply { searches["MOVIE:example movie"] = listOf(candidate) }
        val source = movie(null).copy(
            identities = listOf(ImportedSourceIdentity(ImportedIdentityNamespace.TV_TIME, "movie-uuid"))
        )
        val review = TvTimeMatcher(gateway).match(document(movies = listOf(source))).media.single()
        assertEquals(TvTimeMatchConfidence.HIGH_CONFIDENCE, review.confidence)
        assertEquals(TvTimeMatchReason.TITLE_AND_YEAR_UNIQUE, review.reason)
    }

    @Test
    fun titleOnlySeriesAlwaysNeedsManualReview() = runTest {
        val candidate = seriesCandidate("20", "Example Series", 2019)
        val gateway = FakeGateway().apply { searches["SERIES:example series"] = listOf(candidate) }
        val source = series().copy(
            identities = listOf(ImportedSourceIdentity(ImportedIdentityNamespace.TV_TIME, "series-uuid"))
        )
        val review = TvTimeMatcher(gateway).match(document(series = listOf(source))).media.single()
        assertEquals(TvTimeMatchConfidence.AMBIGUOUS, review.confidence)
        assertNull(review.proposed)
        assertEquals(TvTimeReviewAction.UNDECIDED, review.action)
    }

    @Test
    fun sharesOneSeasonRequestForRegularWatchedEpisodes() = runTest {
        val series = seriesCandidate("20", "Example Series", 2019)
        val season =
            Season(
                ExternalMediaRef(MediaSource.TMDB, "20"),
                ExternalMediaRef(MediaSource.TMDB, "200"),
                1,
                episodeCount = 2
            )
        val episodes = listOf(
            Episode(series.externalRef, season.externalRef, ExternalMediaRef(MediaSource.TMDB, "201"), 1, 1, "One"),
            Episode(series.externalRef, season.externalRef, ExternalMediaRef(MediaSource.TMDB, "202"), 1, 2, "Two")
        )
        val gateway = FakeGateway().apply {
            media["TVDB:20"] = listOf(series)
            seasonPayload = com.cydoniancitizen.bingee.data.tmdb.series.TmdbSeasonPayload(season, episodes)
        }
        val sourceSeries = series().copy(
            identities = listOf(ImportedSourceIdentity(ImportedIdentityNamespace.TVDB, "20"))
        )
        val report = TvTimeMatcher(gateway).match(
            document(
                series = listOf(sourceSeries),
                episodes = listOf(episode(1), episode(2))
            )
        )
        assertTrue(report.episodes.all { it.confidence == TvTimeMatchConfidence.EXACT })
        assertEquals(1, gateway.seasonCalls)
    }

    @Test
    fun specialWithoutExactIdentityRemainsAmbiguous() = runTest {
        val series = seriesCandidate("20", "Example Series", 2019)
        val season = Season(series.externalRef, ExternalMediaRef(MediaSource.TMDB, "200"), 0, episodeCount = 1)
        val canonical =
            Episode(series.externalRef, season.externalRef, ExternalMediaRef(MediaSource.TMDB, "201"), 0, 1, "Special")
        val gateway = FakeGateway().apply {
            media["TVDB:20"] = listOf(series)
            seasonPayload = com.cydoniancitizen.bingee.data.tmdb.series.TmdbSeasonPayload(season, listOf(canonical))
        }
        val sourceSeries = series().copy(
            identities = listOf(ImportedSourceIdentity(ImportedIdentityNamespace.TVDB, "20"))
        )
        val review = TvTimeMatcher(gateway).match(
            document(
                series = listOf(sourceSeries),
                episodes = listOf(episode(1).copy(seasonNumber = 0, special = true, specialsSeason = true))
            )
        ).episodes.single()
        assertEquals(TvTimeMatchConfidence.AMBIGUOUS, review.confidence)
        assertEquals(TvTimeMatchReason.SPECIAL_REQUIRES_REVIEW, review.reason)
    }

    @Test
    fun tvdbMovieIdentityIsNeverSentToUnsupportedTmdbFindRoute() = runTest {
        val gateway = FakeGateway().apply { media["TVDB:101"] = listOf(movieCandidate("10", "Movie", 2020)) }
        val source = movie(null).copy(
            identities = listOf(
                ImportedSourceIdentity(ImportedIdentityNamespace.TVDB, "101"),
                ImportedSourceIdentity(ImportedIdentityNamespace.TV_TIME, "movie-uuid")
            )
        )
        val review = TvTimeMatcher(gateway).match(document(movies = listOf(source))).media.single()
        assertEquals(TvTimeMatchConfidence.UNMATCHED, review.confidence)
        assertEquals(0, gateway.findMediaCalls)
    }

    @Test
    fun exactIdsMustResolveConsistentlyAndRespectMediaType() = runTest {
        val tvdbCandidate = seriesCandidate("20", "Series", 2019)
        val imdbCandidate = seriesCandidate("21", "Other Series", 2019)
        val gateway = FakeGateway().apply {
            media["TVDB:20"] = listOf(tvdbCandidate)
            media["IMDB:tt20"] = listOf(imdbCandidate)
        }
        val source = series().copy(
            identities = listOf(
                ImportedSourceIdentity(ImportedIdentityNamespace.TVDB, "20"),
                ImportedSourceIdentity(ImportedIdentityNamespace.IMDB, "tt20")
            )
        )
        val conflict = TvTimeMatcher(gateway).match(document(series = listOf(source))).media.single()
        assertEquals(TvTimeMatchConfidence.AMBIGUOUS, conflict.confidence)
        assertEquals(TvTimeMatchReason.CONFLICTING_EXTERNAL_IDS, conflict.reason)

        val wrongTypeGateway = FakeGateway().apply {
            media["IMDB:tt123"] = listOf(seriesCandidate("30", "Wrong", 2020))
        }
        val mismatch = TvTimeMatcher(wrongTypeGateway).match(document(movies = listOf(movie("tt123"))))
            .media.single()
        assertEquals(TvTimeMatchConfidence.AMBIGUOUS, mismatch.confidence)
        assertEquals(TvTimeMatchReason.MEDIA_TYPE_MISMATCH, mismatch.reason)
    }

    @Test
    fun providerFailureNeverAutoAcceptsPartialExactResultAndCanRetry() = runTest {
        val candidate = seriesCandidate("20", "Series", 2019)
        val gateway = FakeGateway().apply {
            media["TVDB:20"] = listOf(candidate)
            mediaFailures["IMDB:tt20"] = AppError.RateLimited
        }
        val source = series().copy(
            identities = listOf(
                ImportedSourceIdentity(ImportedIdentityNamespace.TVDB, "20"),
                ImportedSourceIdentity(ImportedIdentityNamespace.IMDB, "tt20")
            )
        )
        val matcher = TvTimeMatcher(gateway)
        val first = matcher.match(document(series = listOf(source))).media.single()
        assertEquals(TvTimeMatchConfidence.AMBIGUOUS, first.confidence)
        assertEquals(TvTimeMatchReason.PROVIDER_ERROR, first.reason)
        gateway.mediaFailures.clear()
        gateway.media["IMDB:tt20"] = listOf(candidate)
        val retried = matcher.match(document(series = listOf(source))).media.single()
        assertEquals(TvTimeMatchConfidence.EXACT, retried.confidence)
    }

    @Test
    fun titleCollisionAndWrongTypeSearchRemainManual() = runTest {
        val source = movie(null).copy(
            identities = listOf(ImportedSourceIdentity(ImportedIdentityNamespace.TV_TIME, "movie-uuid"))
        )
        val collisionGateway = FakeGateway().apply {
            searches["MOVIE:example movie"] = listOf(
                movieCandidate("10", "Example Movie", 2020),
                movieCandidate("11", "Example Movie", 2020)
            )
        }
        val collision = TvTimeMatcher(collisionGateway).match(document(movies = listOf(source))).media.single()
        assertEquals(TvTimeMatchConfidence.AMBIGUOUS, collision.confidence)
        assertEquals(TvTimeMatchReason.MULTIPLE_CANDIDATES, collision.reason)

        val mismatchGateway = FakeGateway().apply {
            searches["MOVIE:example movie"] = listOf(seriesCandidate("20", "Example Movie", 2020))
        }
        val mismatch = TvTimeMatcher(mismatchGateway).match(document(movies = listOf(source))).media.single()
        assertEquals(TvTimeMatchReason.MEDIA_TYPE_MISMATCH, mismatch.reason)
    }

    @Test
    fun unwatchedEpisodesCauseNoSeasonRequest() = runTest {
        val candidate = seriesCandidate("20", "Example Series", 2019)
        val gateway = FakeGateway().apply { media["TVDB:20"] = listOf(candidate) }
        val sourceSeries = series().copy(
            identities = listOf(ImportedSourceIdentity(ImportedIdentityNamespace.TVDB, "20"))
        )
        val sourceEpisode = episode(1).copy(watch = ImportedWatchRecord(false, null, 0, 0))
        val report = TvTimeMatcher(gateway).match(
            document(series = listOf(sourceSeries), episodes = listOf(sourceEpisode))
        )
        assertTrue(report.episodes.isEmpty())
        assertEquals(0, gateway.seasonCalls)
    }

    @Test
    fun duplicateTitleSearchesShareOneSessionRequest() = runTest {
        val first = movie(null).copy(
            identities = listOf(ImportedSourceIdentity(ImportedIdentityNamespace.TV_TIME, "movie-a"))
        )
        val second = first.copy(
            recordId = "movie:1",
            identities = listOf(ImportedSourceIdentity(ImportedIdentityNamespace.TV_TIME, "movie-b"))
        )
        val gateway = FakeGateway().apply {
            searches["MOVIE:example movie"] = listOf(movieCandidate("10", "Example Movie", 2020))
        }

        TvTimeMatcher(gateway).match(document(movies = listOf(first, second)))

        assertEquals(1, gateway.searchCalls)
    }

    private fun document(
        movies: List<ImportedMediaHint> = emptyList(),
        series: List<ImportedMediaHint> = emptyList(),
        episodes: List<ImportedEpisodeHint> = emptyList()
    ) = ImportedSourceDocument(
        profileId = TV_TIME_PROFILE_ID,
        movies = movies,
        series = series,
        episodes = episodes,
        summary = ImportedSourceSummary(movies.size, series.size, 0, episodes.size, 0, episodes.size, 0, 0, 0, 0, 0, ImportedUnsupportedFields()),
        warnings = emptyList()
    )

    private fun movie(imdb: String?) = ImportedMediaHint(
        recordId = "movie:0",
        mediaType = MediaType.MOVIE,
        title = "Example Movie",
        normalizedTitle = "example movie",
        year = 2020,
        createdAt = timestamp(),
        identities = listOfNotNull(imdb?.let { ImportedSourceIdentity(ImportedIdentityNamespace.IMDB, it) }),
        watch = ImportedWatchRecord(false, null, 0, null),
        sourceLocation = location(),
        warnings = emptyList()
    )

    private fun series() = ImportedMediaHint(
        recordId = "series:0",
        mediaType = MediaType.SERIES,
        title = "Example Series",
        normalizedTitle = "example series",
        year = null,
        createdAt = timestamp(),
        identities = listOf(ImportedSourceIdentity(ImportedIdentityNamespace.TV_TIME, "series-uuid")),
        watch = null,
        sourceLocation = location(),
        warnings = emptyList()
    )

    private fun episode(number: Int) = ImportedEpisodeHint(
        recordId = "series:0/season:1/episode:$number",
        parentRecordId = "series:0",
        parentIdentities = listOf(ImportedSourceIdentity(ImportedIdentityNamespace.TVDB, "20")),
        seasonNumber = 1,
        episodeNumber = number,
        title = "Episode $number",
        normalizedTitle = "episode $number",
        special = false,
        specialsSeason = false,
        identities = emptyList(),
        watch = ImportedWatchRecord(true, timestamp(), 0, 0),
        sourceLocation = location(),
        warnings = emptyList()
    )

    private fun timestamp() = ImportedTimestamp("2024-01-01T00:00:00Z", Instant.parse("2024-01-01T00:00:00Z"), 0)
    private fun location() = ImportSourceLocation(0, 0, "$.synthetic")
    private fun movieCandidate(id: String, title: String, year: Int) =
        TmdbImportCandidate(ExternalMediaRef(MediaSource.TMDB, id), MediaType.MOVIE, title, null, year, null, null)
    private fun seriesCandidate(id: String, title: String, year: Int) =
        TmdbImportCandidate(ExternalMediaRef(MediaSource.TMDB, id), MediaType.SERIES, title, null, year, null, null)

    private class FakeGateway : TvTimeTmdbGateway {
        val media = mutableMapOf<String, List<TmdbImportCandidate>>()
        val mediaFailures = mutableMapOf<String, AppError>()
        val searches = mutableMapOf<String, List<TmdbImportCandidate>>()
        var seasonPayload: com.cydoniancitizen.bingee.data.tmdb.series.TmdbSeasonPayload? = null
        var seasonCalls = 0
        var findMediaCalls = 0
        var searchCalls = 0

        override suspend fun findMedia(
            identity: String,
            namespace: String,
            mediaType: MediaType
        ): AppResult<List<TmdbImportCandidate>> {
            findMediaCalls++
            val key = "$namespace:$identity"
            return mediaFailures[key]?.let { AppResult.Failure(it) } ?: AppResult.Success(media[key].orEmpty())
        }

        override suspend fun findEpisodes(
            identity: String,
            namespace: String
        ): AppResult<List<TmdbImportEpisodeCandidate>> = AppResult.Success(emptyList())

        override suspend fun searchMedia(
            mediaType: MediaType,
            title: String,
            year: Int?
        ): AppResult<List<TmdbImportCandidate>> {
            searchCalls++
            return AppResult.Success(searches["${mediaType.name}:${title.lowercase()}"].orEmpty())
        }

        override suspend fun loadDetails(
            candidate: TmdbImportCandidate
        ): AppResult<com.cydoniancitizen.bingee.data.tmdb.details.TmdbMediaDetailsPayload> = error("not used")

        override suspend fun loadSeason(
            seriesTmdbId: Long,
            seasonNumber: Int
        ): AppResult<com.cydoniancitizen.bingee.data.tmdb.series.TmdbSeasonPayload> {
            seasonCalls++
            return AppResult.Success(checkNotNull(seasonPayload))
        }
    }
}
