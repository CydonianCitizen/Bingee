package com.cydoniancitizen.bingee.data.progress

import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.MovieWatchState
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.library.local.EpisodeEntity
import com.cydoniancitizen.bingee.data.library.local.EpisodeWatchProgressEntity
import com.cydoniancitizen.bingee.data.library.local.MediaEntity
import com.cydoniancitizen.bingee.data.library.local.MovieProgressRow
import com.cydoniancitizen.bingee.data.library.local.MovieWatchProgressEntity
import com.cydoniancitizen.bingee.data.library.local.ProgressWriteOutcome
import com.cydoniancitizen.bingee.data.library.local.SeasonEntity
import com.cydoniancitizen.bingee.data.library.local.SeriesWatchProgressEntity
import com.cydoniancitizen.bingee.data.library.local.WatchProgressDao
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultWatchProgressRepositoryTest {
    private val now = Instant.parse("2026-08-03T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun movieObservationAndWritesUseLocalClockAndMapTypeFailures() = runTest {
        val dao = FakeProgressDao()
        val repository = DefaultWatchProgressRepository(dao, clock)
        val movie = ref("200")

        assertEquals(AppResult.Success(MovieWatchState.Unwatched), repository.observeMovie(movie).first())
        assertEquals(AppResult.Success(Unit), repository.markMovieWatched(movie))
        assertEquals(now, dao.lastInstant)
        dao.movie.value = MovieProgressRow(MediaType.MOVIE, now)
        assertEquals(AppResult.Success(MovieWatchState.Watched(now)), repository.observeMovie(movie).first())

        dao.outcome = ProgressWriteOutcome.MEDIA_TYPE_MISMATCH
        assertEquals(
            AppResult.Failure(AppError.MediaTypeMismatch),
            repository.markMovieWatched(ref("100"))
        )
    }

    @Test
    fun episodeAndSeasonOutcomesAreSafeAndUseExactUtcDateBoundary() = runTest {
        val dao = FakeProgressDao()
        val repository = DefaultWatchProgressRepository(dao, clock)

        assertEquals(AppResult.Success(Unit), repository.markEpisodeWatched(ref("101")))
        assertEquals(LocalDate.of(2026, 8, 3), dao.lastDate)
        assertEquals(now, dao.lastInstant)
        assertEquals(AppResult.Success(Unit), repository.markEpisodeUnwatched(ref("101")))
        assertEquals(AppResult.Success(Unit), repository.markSeasonWatched(ref("11")))
        assertEquals(AppResult.Success(Unit), repository.markSeasonUnwatched(ref("11")))

        dao.outcome = ProgressWriteOutcome.NOT_TRACKABLE
        assertEquals(
            AppResult.Failure(AppError.NotTrackable),
            repository.markEpisodeWatched(ref("103"))
        )
        dao.outcome = ProgressWriteOutcome.NOT_FOUND
        assertEquals(
            AppResult.Failure(AppError.MissingData),
            repository.markSeasonUnwatched(ref("404"))
        )
    }

    @Test
    fun unsupportedProviderAndInvalidIdentityNeverReachDao() = runTest {
        val dao = FakeProgressDao()
        val repository = DefaultWatchProgressRepository(dao, clock)

        assertEquals(
            AppResult.Failure(AppError.UnsupportedData),
            repository.markEpisodeWatched(ExternalMediaRef(MediaSource.IMDB, "1"))
        )
        assertEquals(
            AppResult.Failure(AppError.InvalidInput),
            repository.markMovieWatched(ref("invalid"))
        )
        assertEquals(0, dao.writeCount)
    }

    private fun ref(id: String) = ExternalMediaRef(MediaSource.TMDB, id)

    private class FakeProgressDao : WatchProgressDao() {
        val movie = MutableStateFlow<MovieProgressRow?>(MovieProgressRow(MediaType.MOVIE, null))
        var outcome = ProgressWriteOutcome.SUCCESS
        var lastDate: LocalDate? = null
        var lastInstant: Instant? = null
        var writeCount = 0

        override fun observeMovieProgress(source: MediaSource, externalId: String): Flow<MovieProgressRow?> = movie

        override suspend fun markEpisodeWatched(
            source: MediaSource,
            externalId: String,
            today: LocalDate,
            watchedAt: Instant
        ): ProgressWriteOutcome = record(today, watchedAt)

        override suspend fun markEpisodeUnwatched(source: MediaSource, externalId: String): ProgressWriteOutcome =
            record()

        override suspend fun markSeasonWatched(
            source: MediaSource,
            externalId: String,
            today: LocalDate,
            watchedAt: Instant
        ): ProgressWriteOutcome = record(today, watchedAt)

        override suspend fun markSeasonUnwatched(source: MediaSource, externalId: String): ProgressWriteOutcome =
            record()

        override suspend fun markMovieWatched(
            source: MediaSource,
            externalId: String,
            watchedAt: Instant,
            watchedDate: LocalDate?
        ): ProgressWriteOutcome = record(date = watchedDate, at = watchedAt)

        override suspend fun setMediaWatchedDate(
            source: MediaSource,
            externalId: String,
            watchedDate: LocalDate?,
            now: Instant
        ): ProgressWriteOutcome = record(date = watchedDate, at = now)

        override suspend fun markMovieUnwatched(source: MediaSource, externalId: String): ProgressWriteOutcome =
            record()

        private fun record(date: LocalDate? = null, at: Instant? = null): ProgressWriteOutcome {
            writeCount++
            lastDate = date
            lastInstant = at
            return outcome
        }

        override suspend fun getEpisode(source: MediaSource, externalId: String): EpisodeEntity? = null
        override suspend fun getSeason(source: MediaSource, externalId: String): SeasonEntity? = null
        override suspend fun getMedia(source: MediaSource, externalId: String): MediaEntity? = null
        override suspend fun getTrackableEpisodeIds(localSeasonId: Long, today: LocalDate): List<Long> = emptyList()
        override suspend fun getMovieProgressByMediaId(localMediaId: Long): MovieWatchProgressEntity? = null
        override suspend fun getSeriesProgressByMediaId(localMediaId: Long): SeriesWatchProgressEntity? = null
        override suspend fun insertSeriesProgress(progress: SeriesWatchProgressEntity): Long = 1
        override suspend fun deleteSeriesProgress(localMediaId: Long) = Unit
        override suspend fun insertEpisodeProgress(progress: EpisodeWatchProgressEntity): Long = 1
        override suspend fun insertEpisodeProgress(progress: List<EpisodeWatchProgressEntity>) = Unit
        override suspend fun insertMovieProgress(progress: MovieWatchProgressEntity): Long = 1
        override suspend fun deleteEpisodeProgress(localEpisodeId: Long) = Unit
        override suspend fun deleteSeasonProgress(localSeasonId: Long) = Unit
        override suspend fun deleteMovieProgress(localMediaId: Long) = Unit
    }
}
