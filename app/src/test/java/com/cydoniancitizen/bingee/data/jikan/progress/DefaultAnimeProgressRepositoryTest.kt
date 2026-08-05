package com.cydoniancitizen.bingee.data.jikan.progress

import android.database.sqlite.SQLiteException
import com.cydoniancitizen.bingee.core.model.AnimeCompletionOrigin
import com.cydoniancitizen.bingee.core.model.AnimeFormat
import com.cydoniancitizen.bingee.core.model.AnimeProgressState
import com.cydoniancitizen.bingee.core.model.AnimeStatus
import com.cydoniancitizen.bingee.core.model.AnimeWatchProgress
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.library.local.AnimeDao
import com.cydoniancitizen.bingee.data.library.local.AnimeDetailsEntity
import com.cydoniancitizen.bingee.data.library.local.AnimeProgressEntity
import com.cydoniancitizen.bingee.data.library.local.AnimeRelationEntity
import com.cydoniancitizen.bingee.data.library.local.CachedAnimeRelation
import com.cydoniancitizen.bingee.data.library.local.ExternalRefEntity
import com.cydoniancitizen.bingee.data.library.local.MediaEntity
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultAnimeProgressRepositoryTest {
    private val now = Instant.parse("2026-08-05T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val animeRef = ExternalMediaRef(MediaSource.JIKAN, "52991")

    @Test
    fun initialStateZeroAndIncrementDecrementAreLocal() = runTest {
        val dao = FakeAnimeDao()
        val repository = DefaultAnimeProgressRepository(dao, clock)

        assertEquals(AppResult.Success<AnimeWatchProgress?>(null), repository.observe(animeRef).first())
        assertEquals(AppResult.Success(Unit), repository.decrement(animeRef))
        assertEquals(0, dao.progress()?.watchedEpisodeCount)
        assertEquals(AppResult.Success(Unit), repository.increment(animeRef))
        assertEquals(1, dao.progress()?.watchedEpisodeCount)
        assertEquals(AppResult.Success(Unit), repository.decrement(animeRef))
        assertEquals(0, dao.progress()?.watchedEpisodeCount)
    }

    @Test
    fun directCountSupportsZeroAndRejectsNegativeOrGlobalMaximumOverflow() = runTest {
        val dao = FakeAnimeDao()
        val repository = DefaultAnimeProgressRepository(dao, clock)

        assertEquals(AppResult.Success(Unit), repository.setCount(animeRef, 7))
        assertEquals(7, dao.progress()?.watchedEpisodeCount)
        assertEquals(
            AppResult.Failure(AppError.InvalidInput),
            repository.setCount(animeRef, -1)
        )
        assertEquals(
            AppResult.Failure(AppError.InvalidInput),
            repository.setCount(animeRef, AnimeWatchProgress.MAX_WATCHED_EPISODES + 1)
        )
        assertEquals(7, dao.progress()?.watchedEpisodeCount)
    }

    @Test
    fun knownTotalInfersCompletionAndReductionClearsIt() = runTest {
        val dao = FakeAnimeDao(details = details(episodeCount = 3))
        val repository = DefaultAnimeProgressRepository(dao, clock)

        assertEquals(AppResult.Success(Unit), repository.setCount(animeRef, 3))
        assertEquals(AnimeCompletionOrigin.INFERRED, dao.progress()?.completionOrigin)
        assertEquals(now, dao.progress()?.completedAt)

        assertEquals(AppResult.Success(Unit), repository.setCount(animeRef, 2))
        assertEquals(2, dao.progress()?.watchedEpisodeCount)
        assertEquals(null, dao.progress()?.completedAt)
        assertEquals(null, dao.progress()?.completionOrigin)
    }

    @Test
    fun unknownAndOngoingExplicitCompletionSurvivesEditsUntilExplicitIncomplete() = runTest {
        val unknownDao = FakeAnimeDao(details = details(episodeCount = null, status = AnimeStatus.UNKNOWN))
        val unknownRepository = DefaultAnimeProgressRepository(unknownDao, clock)

        assertEquals(AppResult.Success(Unit), unknownRepository.markComplete(animeRef))
        assertEquals(AnimeCompletionOrigin.EXPLICIT, unknownDao.progress()?.completionOrigin)
        assertEquals(AppResult.Success(Unit), unknownRepository.setCount(animeRef, 4))
        assertEquals(4, unknownDao.progress()?.watchedEpisodeCount)
        assertEquals(now, unknownDao.progress()?.completedAt)
        assertEquals(AppResult.Success(Unit), unknownRepository.markIncomplete(animeRef))
        assertEquals(4, unknownDao.progress()?.watchedEpisodeCount)
        assertEquals(null, unknownDao.progress()?.completedAt)

        val airingDao = FakeAnimeDao(details = details(episodeCount = 12, status = AnimeStatus.AIRING))
        val airingRepository = DefaultAnimeProgressRepository(airingDao, clock)
        assertEquals(AppResult.Success(Unit), airingRepository.markComplete(animeRef))
        assertEquals(AppResult.Success(Unit), airingRepository.setCount(animeRef, 2))
        assertEquals(AnimeCompletionOrigin.EXPLICIT, airingDao.progress()?.completionOrigin)
        assertEquals(now, airingDao.progress()?.completedAt)
    }

    @Test
    fun metadataTotalReductionMakesExistingPositiveCountComplete() = runTest {
        val dao = FakeAnimeDao(details = details(episodeCount = 12))
        val repository = DefaultAnimeProgressRepository(dao, clock)

        assertEquals(AppResult.Success(Unit), repository.setCount(animeRef, 8))
        dao.updateCachedDetails(details(episodeCount = 8))

        val progress = (repository.observe(animeRef).first() as AppResult.Success).value!!
        assertEquals(AnimeProgressState.COMPLETED, progress.state(8))
        assertFalse(progress.state(12) == AnimeProgressState.COMPLETED)
    }

    @Test
    fun movieUsesZeroOrOneAndOverflowIsAResultFailure() = runTest {
        val dao = FakeAnimeDao(details = details(format = AnimeFormat.MOVIE, episodeCount = 1))
        val repository = DefaultAnimeProgressRepository(dao, clock)

        assertEquals(AppResult.Success(Unit), repository.markComplete(animeRef))
        assertEquals(1, dao.progress()?.watchedEpisodeCount)
        assertEquals(AppResult.Success(Unit), repository.markIncomplete(animeRef))
        assertEquals(0, dao.progress()?.watchedEpisodeCount)
        assertEquals(AppResult.Success(Unit), repository.markComplete(animeRef))
        assertEquals(
            AppResult.Failure(AppError.InvalidInput),
            repository.increment(animeRef)
        )
        assertEquals(
            AppResult.Failure(AppError.InvalidInput),
            repository.setCount(animeRef, 2)
        )
        assertEquals(1, dao.progress()?.watchedEpisodeCount)
    }

    @Test
    fun providerAndMediaTypeMismatchNeverCorruptsProgress() = runTest {
        val dao = FakeAnimeDao()
        val repository = DefaultAnimeProgressRepository(dao, clock)

        assertEquals(
            AppResult.Failure(AppError.InvalidInput),
            repository.increment(ExternalMediaRef(MediaSource.TMDB, animeRef.externalId))
        )
        assertEquals(0, dao.writeCount)

        dao.mediaTypeMismatch = true
        assertEquals(
            AppResult.Failure(AppError.MediaTypeMismatch),
            repository.increment(animeRef)
        )
        assertEquals(null, dao.progress()?.watchedEpisodeCount)
    }

    @Test
    fun repeatedIdenticalWriteIsIdempotentAndPersistenceFailureIsReported() = runTest {
        val dao = FakeAnimeDao()
        val repository = DefaultAnimeProgressRepository(dao, clock)

        assertEquals(AppResult.Success(Unit), repository.setCount(animeRef, 2))
        val first = dao.progress()
        assertEquals(AppResult.Success(Unit), repository.setCount(animeRef, 2))
        assertEquals(first, dao.progress())

        dao.writeFailure = SQLiteException("write failed")
        assertEquals(
            AppResult.Failure(AppError.LocalStorageFailure),
            repository.increment(animeRef)
        )
        assertEquals(first, dao.progress())
    }

    @Test
    fun cancellationPropagatesFromReadAndWrite() = runTest {
        val readDao = FakeAnimeDao().apply { readFailure = CancellationException("cancelled") }
        val readRepository = DefaultAnimeProgressRepository(readDao, clock)
        try {
            readRepository.increment(animeRef)
            throw AssertionError("Expected read cancellation")
        } catch (_: CancellationException) {
            // Cancellation is never converted to an application error.
        }

        val writeDao = FakeAnimeDao().apply { writeFailure = CancellationException("cancelled") }
        val writeRepository = DefaultAnimeProgressRepository(writeDao, clock)
        try {
            writeRepository.increment(animeRef)
            throw AssertionError("Expected write cancellation")
        } catch (_: CancellationException) {
            // Cancellation is never converted to an application error.
        }
    }

    private fun details(
        format: AnimeFormat = AnimeFormat.TV,
        status: AnimeStatus = AnimeStatus.FINISHED,
        episodeCount: Int? = 12
    ) = AnimeDetailsEntity(
        localMediaId = 1,
        format = format,
        providerStatus = status,
        englishTitle = "Fixture",
        japaneseTitle = "作品",
        synopsis = null,
        episodeCount = episodeCount,
        duration = null,
        startDate = LocalDate.of(2025, 1, 1),
        endDate = null,
        season = null,
        year = 2025,
        providerScore = null,
        imageUrl = null,
        detailsUpdatedAt = now
    )

    private class FakeAnimeDao(
        details: AnimeDetailsEntity? = defaultDetails(),
        initialProgress: AnimeProgressEntity? = null
    ) : AnimeDao() {
        private val row = MutableStateFlow(
            CachedAnimeRelation(
                media = MediaEntity(
                    localMediaId = 1,
                    mediaType = MediaType.ANIME,
                    title = "Fixture",
                    originalTitle = "作品",
                    overview = null,
                    posterUrl = null,
                    releaseDate = LocalDate.of(2025, 1, 1),
                    createdAt = Instant.parse("2026-08-01T10:00:00Z"),
                    metadataUpdatedAt = Instant.parse("2026-08-05T10:00:00Z")
                ),
                details = details,
                externalRefs = listOf(ExternalRefEntity(1, MediaSource.JIKAN, "52991")),
                relations = emptyList(),
                progress = initialProgress
            )
        )
        var writeCount = 0
        var mediaTypeMismatch = false
        var writeFailure: Throwable? = null
        var readFailure: Throwable? = null

        fun progress(): AnimeProgressEntity? = row.value.progress

        fun updateCachedDetails(value: AnimeDetailsEntity) {
            row.value = row.value.copy(details = value)
        }

        override fun observeAnime(externalId: String): Flow<CachedAnimeRelation?> = row

        override suspend fun getAnime(externalId: String): CachedAnimeRelation? {
            readFailure?.let { throw it }
            return row.value
        }

        override suspend fun setProgress(source: MediaSource, externalId: String, progress: AnimeWatchProgress?) {
            writeFailure?.let { throw it }
            if (mediaTypeMismatch) throw IllegalArgumentException("wrong media type")
            writeCount++
            row.value = row.value.copy(
                progress = progress?.let {
                    AnimeProgressEntity(
                        localMediaId = 1,
                        watchedEpisodeCount = it.watchedEpisodes,
                        completedAt = it.completedAt,
                        completionOrigin = it.completionOrigin,
                        updatedAt = it.updatedAt
                    )
                }
            )
        }

        override suspend fun storeAnime(
            media: MediaEntity,
            externalId: String,
            details: AnimeDetailsEntity,
            relations: List<AnimeRelationEntity>
        ): Long = 1

        protected override suspend fun getMedia(source: MediaSource, externalId: String): MediaEntity? = row.value.media
        protected override suspend fun insertMedia(media: MediaEntity): Long = 1
        protected override suspend fun updateMedia(media: MediaEntity) = Unit
        protected override suspend fun insertExternalRef(ref: ExternalRefEntity) = Unit
        protected override suspend fun replaceDetails(details: AnimeDetailsEntity) = Unit
        protected override suspend fun deleteRelations(localMediaId: Long) = Unit
        protected override suspend fun insertRelations(relations: List<AnimeRelationEntity>) = Unit
        protected override suspend fun replaceProgress(progress: AnimeProgressEntity) = Unit
        protected override suspend fun deleteProgress(localMediaId: Long) = Unit

        companion object {
            private fun defaultDetails() = AnimeDetailsEntity(
                localMediaId = 1,
                format = AnimeFormat.TV,
                providerStatus = AnimeStatus.FINISHED,
                englishTitle = "Fixture",
                japaneseTitle = "作品",
                synopsis = null,
                episodeCount = 12,
                duration = null,
                startDate = LocalDate.of(2025, 1, 1),
                endDate = null,
                season = null,
                year = 2025,
                providerScore = null,
                imageUrl = null,
                detailsUpdatedAt = Instant.parse("2026-08-05T10:00:00Z")
            )
        }
    }
}
