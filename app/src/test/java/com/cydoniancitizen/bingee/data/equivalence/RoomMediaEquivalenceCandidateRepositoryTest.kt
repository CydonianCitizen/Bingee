package com.cydoniancitizen.bingee.data.equivalence

import com.cydoniancitizen.bingee.core.model.AnimeFormat
import com.cydoniancitizen.bingee.core.model.LinkedMediaIdentity
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.library.local.AnimeRelationEntity
import com.cydoniancitizen.bingee.data.library.local.ExternalRefEntity
import com.cydoniancitizen.bingee.data.library.local.FullCandidateData
import com.cydoniancitizen.bingee.data.library.local.MediaEquivalenceCandidateDao
import com.cydoniancitizen.bingee.data.library.local.RawCandidateProjection
import com.cydoniancitizen.bingee.domain.equivalence.MediaEquivalenceClassification
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomMediaEquivalenceCandidateRepositoryTest {

    private val fakeDao = FakeCandidateDao()
    private val repository = RoomMediaEquivalenceCandidateRepository(fakeDao)

    @Test
    fun observeLibraryCandidates_emitsExactAndStrongCandidates() = runTest {
        val tmdbMovie = fullData(
            localId = 1,
            type = MediaType.MOVIE,
            title = "Spirited Away",
            year = 2001,
            sourceRef = ExternalRefEntity(1, MediaSource.TMDB, "129"),
            imdbRef = ExternalRefEntity(1, MediaSource.IMDB, "tt0245429")
        )

        val jikanMovie = fullData(
            localId = 2,
            type = MediaType.ANIME,
            title = "Sen to Chihiro no Kamikakushi",
            animeFormat = AnimeFormat.MOVIE,
            animeYear = 2001,
            sourceRef = ExternalRefEntity(2, MediaSource.JIKAN, "199"),
            imdbRef = ExternalRefEntity(2, MediaSource.IMDB, "tt0245429")
        )

        fakeDao.candidatesFlow.value = listOf(tmdbMovie, jikanMovie)

        val candidates = repository.observeLibraryCandidates().first()

        assertEquals(1, candidates.size)
        assertEquals(MediaEquivalenceClassification.EXACT_IDENTITY, candidates.first().evaluation.classification)
    }

    @Test
    fun observeLibraryCandidates_excludesActiveLinkedMembers() = runTest {
        val tmdbMovie = fullData(
            localId = 1,
            type = MediaType.MOVIE,
            title = "Spirited Away",
            year = 2001,
            sourceRef = ExternalRefEntity(1, MediaSource.TMDB, "129"),
            imdbRef = ExternalRefEntity(1, MediaSource.IMDB, "tt0245429")
        )

        val jikanMovie = fullData(
            localId = 2,
            type = MediaType.ANIME,
            title = "Sen to Chihiro no Kamikakushi",
            animeFormat = AnimeFormat.MOVIE,
            animeYear = 2001,
            sourceRef = ExternalRefEntity(2, MediaSource.JIKAN, "199"),
            imdbRef = ExternalRefEntity(2, MediaSource.IMDB, "tt0245429")
        )

        fakeDao.candidatesFlow.value = listOf(tmdbMovie, jikanMovie)
        fakeDao.linkedIdsFlow.value = listOf(1L) // Local ID 1 is linked

        val candidates = repository.observeLibraryCandidates().first()
        assertTrue(candidates.isEmpty())
    }

    @Test
    fun evaluatePair_suspendExplicitEvaluation() = runTest {
        val tmdbMovie = fullData(
            localId = 1,
            type = MediaType.MOVIE,
            title = "Spirited Away",
            year = 2001,
            sourceRef = ExternalRefEntity(1, MediaSource.TMDB, "129"),
            imdbRef = ExternalRefEntity(1, MediaSource.IMDB, "tt0245429")
        )

        val jikanMovie = fullData(
            localId = 2,
            type = MediaType.ANIME,
            title = "Sen to Chihiro no Kamikakushi",
            animeFormat = AnimeFormat.MOVIE,
            animeYear = 2001,
            sourceRef = ExternalRefEntity(2, MediaSource.JIKAN, "199"),
            imdbRef = ExternalRefEntity(2, MediaSource.IMDB, "tt0245429")
        )

        fakeDao.candidatesList = listOf(tmdbMovie, jikanMovie)

        val firstId = LinkedMediaIdentity(MediaSource.TMDB, MediaType.MOVIE, "129")
        val secondId = LinkedMediaIdentity(MediaSource.JIKAN, MediaType.ANIME, "199")

        val result = repository.evaluatePair(firstId, secondId)
        assertTrue(result is AppResult.Success)
        val eval = (result as AppResult.Success).value
        assertEquals(MediaEquivalenceClassification.EXACT_IDENTITY, eval.classification)
    }

    @Test
    fun performance_1000TitlesBoundedExecution() = runTest {
        val dataset = mutableListOf<FullCandidateData>()
        // 500 TMDB movies
        for (i in 1..500) {
            dataset.add(
                fullData(
                    localId = i.toLong(),
                    type = MediaType.MOVIE,
                    title = "Movie $i",
                    year = 2000 + (i % 20),
                    sourceRef = ExternalRefEntity(i.toLong(), MediaSource.TMDB, "$i"),
                    imdbRef = if (i % 10 == 0) ExternalRefEntity(i.toLong(), MediaSource.IMDB, "tt$i") else null
                )
            )
        }
        // 500 Jikan anime movies
        for (i in 501..1000) {
            val j = i - 500
            dataset.add(
                fullData(
                    localId = i.toLong(),
                    type = MediaType.ANIME,
                    title = "Movie $j",
                    animeFormat = AnimeFormat.MOVIE,
                    animeYear = 2000 + (j % 20),
                    sourceRef = ExternalRefEntity(i.toLong(), MediaSource.JIKAN, "$i"),
                    imdbRef = if (j % 10 == 0) ExternalRefEntity(i.toLong(), MediaSource.IMDB, "tt$j") else null
                )
            )
        }

        fakeDao.candidatesFlow.value = dataset

        val startTime = System.currentTimeMillis()
        val candidates = repository.observeLibraryCandidates().first()
        val durationMs = System.currentTimeMillis() - startTime

        assertTrue(
            "Execution should finish quickly without O(n^2) Cartesian product scan, took $durationMs ms",
            durationMs < 2000
        )
        assertTrue("Found plausible candidates", candidates.isNotEmpty())
    }

    private fun fullData(
        localId: Long,
        type: MediaType,
        title: String,
        year: Int? = null,
        animeFormat: AnimeFormat? = null,
        animeYear: Int? = null,
        sourceRef: ExternalRefEntity,
        imdbRef: ExternalRefEntity? = null,
        relations: List<AnimeRelationEntity> = emptyList()
    ): FullCandidateData {
        val refs = mutableListOf(sourceRef)
        imdbRef?.let { refs.add(it) }
        return FullCandidateData(
            raw = RawCandidateProjection(
                localMediaId = localId,
                mediaType = type,
                title = title,
                originalTitle = null,
                releaseDate = year?.let { LocalDate.of(it, 1, 1) },
                numberOfSeasons = null,
                animeFormat = animeFormat,
                englishTitle = null,
                japaneseTitle = null,
                animeYear = animeYear,
                animeStartDate = animeYear?.let { LocalDate.of(it, 1, 1) },
                isLibraryMember = true
            ),
            externalRefs = refs,
            animeRelations = relations
        )
    }

    private class FakeCandidateDao : MediaEquivalenceCandidateDao() {
        val candidatesFlow = MutableStateFlow<List<FullCandidateData>>(emptyList())
        val linkedIdsFlow = MutableStateFlow<List<Long>>(emptyList())

        var candidatesList: List<FullCandidateData> = emptyList()
        var linkedIdsList: List<Long> = emptyList()

        override fun observeAllCandidatesData(): Flow<List<FullCandidateData>> = candidatesFlow
        override suspend fun getAllCandidatesData(): List<FullCandidateData> = candidatesList.ifEmpty {
            candidatesFlow.value
        }

        override fun observeActiveLinkMemberMediaIds(): Flow<List<Long>> = linkedIdsFlow
        override suspend fun getActiveLinkMemberMediaIds(): List<Long> = linkedIdsList.ifEmpty { linkedIdsFlow.value }
    }
}
