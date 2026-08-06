package com.cydoniancitizen.bingee.domain.equivalence

import com.cydoniancitizen.bingee.core.model.AnimeFormat
import com.cydoniancitizen.bingee.core.model.LinkedMediaIdentity
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaEquivalenceEvaluatorTest {

    private val tmdbMovieRef = LinkedMediaIdentity(MediaSource.TMDB, MediaType.MOVIE, "129")
    private val jikanMovieRef = LinkedMediaIdentity(MediaSource.JIKAN, MediaType.ANIME, "199")
    private val tmdbSeriesRef = LinkedMediaIdentity(MediaSource.TMDB, MediaType.SERIES, "38757")
    private val jikanTvRef = LinkedMediaIdentity(MediaSource.JIKAN, MediaType.ANIME, "16498")

    @Test
    fun case1_sharedImdb_exactIdentity() {
        val tmdb = CandidateMediaProjection(
            identity = tmdbMovieRef,
            title = "Spirited Away",
            releaseYear = 2001,
            imdbId = "tt0245429"
        )
        val jikan = CandidateMediaProjection(
            identity = jikanMovieRef,
            title = "Sen to Chihiro no Kamikakushi",
            releaseYear = 2001,
            animeFormat = AnimeFormat.MOVIE,
            imdbId = "tt0245429"
        )
        val eval = MediaEquivalenceEvaluator.evaluate(tmdb, jikan)
        assertEquals(MediaEquivalenceClassification.EXACT_IDENTITY, eval.classification)
        assertTrue(eval.positiveSignals.contains(MediaEquivalenceSignal.SHARED_IMDB_ID))
    }

    @Test
    fun case2_titleYearOriginalTitle_strongPossible() {
        val tmdb = CandidateMediaProjection(
            identity = tmdbMovieRef,
            title = "My Hero Academia: Two Heroes",
            originalTitle = "Boku no Hero Academia Movie 1: Futari no Hero",
            releaseYear = 2018
        )
        val jikan = CandidateMediaProjection(
            identity = jikanMovieRef,
            title = "My Hero Academia: Two Heroes",
            japaneseTitle = "Boku no Hero Academia Movie 1: Futari no Hero",
            releaseYear = 2018,
            animeFormat = AnimeFormat.MOVIE
        )
        val eval = MediaEquivalenceEvaluator.evaluate(tmdb, jikan)
        assertEquals(MediaEquivalenceClassification.STRONG_POSSIBLE_SAME_WORK, eval.classification)
        assertTrue(eval.positiveSignals.contains(MediaEquivalenceSignal.EXACT_RELEASE_YEAR))
        assertTrue(
            eval.positiveSignals.contains(MediaEquivalenceSignal.EXACT_JAPANESE_TITLE) ||
                eval.positiveSignals.contains(MediaEquivalenceSignal.EXACT_ORIGINAL_TITLE)
        )
    }

    @Test
    fun case3_titleOnly_ambiguous() {
        val tmdb = CandidateMediaProjection(
            identity = tmdbMovieRef,
            title = "Monster",
            releaseYear = 2004
        )
        val jikan = CandidateMediaProjection(
            identity = jikanMovieRef,
            title = "Monster",
            releaseYear = 2004,
            animeFormat = AnimeFormat.MOVIE
        )
        val eval = MediaEquivalenceEvaluator.evaluate(tmdb, jikan)
        assertEquals(MediaEquivalenceClassification.AMBIGUOUS, eval.classification)
    }

    @Test
    fun case4_missingYear_ambiguous() {
        val tmdb = CandidateMediaProjection(
            identity = tmdbMovieRef,
            title = "Spirited Away",
            releaseYear = null
        )
        val jikan = CandidateMediaProjection(
            identity = jikanMovieRef,
            title = "Spirited Away",
            releaseYear = 2001,
            animeFormat = AnimeFormat.MOVIE
        )
        val eval = MediaEquivalenceEvaluator.evaluate(tmdb, jikan)
        assertEquals(MediaEquivalenceClassification.AMBIGUOUS, eval.classification)
    }

    @Test
    fun case5_oneYearDifference_ambiguous() {
        val tmdb = CandidateMediaProjection(
            identity = tmdbMovieRef,
            title = "Spirited Away",
            releaseYear = 2001
        )
        val jikan = CandidateMediaProjection(
            identity = jikanMovieRef,
            title = "Spirited Away",
            releaseYear = 2002,
            animeFormat = AnimeFormat.MOVIE
        )
        val eval = MediaEquivalenceEvaluator.evaluate(tmdb, jikan)
        assertEquals(MediaEquivalenceClassification.AMBIGUOUS, eval.classification)
    }

    @Test
    fun case6_yearMismatch_notEquivalent() {
        val tmdb = CandidateMediaProjection(
            identity = tmdbSeriesRef,
            title = "Monster",
            releaseYear = 2004
        )
        val jikan = CandidateMediaProjection(
            identity = jikanMovieRef,
            title = "Monster",
            releaseYear = 2023,
            animeFormat = AnimeFormat.MOVIE
        )
        val eval = MediaEquivalenceEvaluator.evaluate(tmdb, jikan)
        assertEquals(MediaEquivalenceClassification.NOT_EQUIVALENT, eval.classification)
    }

    @Test
    fun case7_conflictingImdb_notEquivalent() {
        val tmdb = CandidateMediaProjection(
            identity = tmdbMovieRef,
            title = "Spirited Away",
            releaseYear = 2001,
            imdbId = "tt0245429"
        )
        val jikan = CandidateMediaProjection(
            identity = jikanMovieRef,
            title = "Spirited Away",
            releaseYear = 2001,
            animeFormat = AnimeFormat.MOVIE,
            imdbId = "tt9999999"
        )
        val eval = MediaEquivalenceEvaluator.evaluate(tmdb, jikan)
        assertEquals(MediaEquivalenceClassification.NOT_EQUIVALENT, eval.classification)
    }

    @Test
    fun case8_movieToTv_notEquivalent() {
        val tmdb = CandidateMediaProjection(
            identity = tmdbMovieRef,
            title = "Attack on Titan",
            releaseYear = 2013
        )
        val jikan = CandidateMediaProjection(
            identity = jikanTvRef,
            title = "Attack on Titan",
            releaseYear = 2013,
            animeFormat = AnimeFormat.TV
        )
        val eval = MediaEquivalenceEvaluator.evaluate(tmdb, jikan)
        assertEquals(MediaEquivalenceClassification.NOT_EQUIVALENT, eval.classification)
    }

    @Test
    fun case9_seriesToMovie_notEquivalent() {
        val tmdb = CandidateMediaProjection(
            identity = tmdbSeriesRef,
            title = "Spirited Away",
            releaseYear = 2001
        )
        val jikan = CandidateMediaProjection(
            identity = jikanMovieRef,
            title = "Spirited Away",
            releaseYear = 2001,
            animeFormat = AnimeFormat.MOVIE
        )
        val eval = MediaEquivalenceEvaluator.evaluate(tmdb, jikan)
        assertEquals(MediaEquivalenceClassification.NOT_EQUIVALENT, eval.classification)
    }

    @Test
    fun case10_singleSeasonSeriesToTv_strongPossible() {
        val tmdb = CandidateMediaProjection(
            identity = tmdbSeriesRef,
            title = "Cyberpunk: Edgerunners",
            originalTitle = "Cyberpunk: Edgerunners",
            releaseYear = 2022,
            tmdbSeasonCount = 1,
            releaseDate = LocalDate.of(2022, 9, 13)
        )
        val jikan = CandidateMediaProjection(
            identity = jikanTvRef,
            title = "Cyberpunk: Edgerunners",
            releaseYear = 2022,
            animeFormat = AnimeFormat.TV,
            releaseDate = LocalDate.of(2022, 9, 13)
        )
        val eval = MediaEquivalenceEvaluator.evaluate(tmdb, jikan)
        assertEquals(MediaEquivalenceClassification.STRONG_POSSIBLE_SAME_WORK, eval.classification)
    }

    @Test
    fun case11_multiSeasonSeries_relatedDistinct() {
        val tmdb = CandidateMediaProjection(
            identity = tmdbSeriesRef,
            title = "Attack on Titan",
            releaseYear = 2013,
            tmdbSeasonCount = 4
        )
        val jikan = CandidateMediaProjection(
            identity = jikanTvRef,
            title = "Attack on Titan",
            releaseYear = 2013,
            animeFormat = AnimeFormat.TV
        )
        val eval = MediaEquivalenceEvaluator.evaluate(tmdb, jikan)
        assertEquals(MediaEquivalenceClassification.RELATED_DISTINCT, eval.classification)
    }

    @Test
    fun case12_sequelRelation_relatedDistinct() {
        val tmdb = CandidateMediaProjection(
            identity = tmdbSeriesRef,
            title = "Attack on Titan S2",
            releaseYear = 2017,
            tmdbSeasonCount = 1
        )
        val jikan = CandidateMediaProjection(
            identity = jikanTvRef,
            title = "Attack on Titan S2",
            releaseYear = 2017,
            animeFormat = AnimeFormat.TV,
            relationTypes = setOf("Sequel")
        )
        val eval = MediaEquivalenceEvaluator.evaluate(tmdb, jikan)
        assertEquals(MediaEquivalenceClassification.RELATED_DISTINCT, eval.classification)
    }

    @Test
    fun case20_sameNumericIdDifferentProvider_notExactById() {
        val tmdb = LinkedMediaIdentity(MediaSource.TMDB, MediaType.MOVIE, "100")
        val jikan = LinkedMediaIdentity(MediaSource.JIKAN, MediaType.ANIME, "100")

        val p1 = CandidateMediaProjection(identity = tmdb, title = "Title A", releaseYear = 2020)
        val p2 =
            CandidateMediaProjection(
                identity = jikan,
                title = "Title B",
                releaseYear = 2021,
                animeFormat = AnimeFormat.MOVIE
            )

        val eval = MediaEquivalenceEvaluator.evaluate(p1, p2)
        assertTrue(eval.classification != MediaEquivalenceClassification.EXACT_IDENTITY)
    }

    @Test
    fun case21_alreadyLinked_invalidCandidate() {
        val tmdb =
            CandidateMediaProjection(
                identity = tmdbMovieRef,
                title = "Spirited Away",
                releaseYear = 2001,
                isAlreadyLinked = true
            )
        val jikan =
            CandidateMediaProjection(
                identity = jikanMovieRef,
                title = "Spirited Away",
                releaseYear = 2001,
                animeFormat = AnimeFormat.MOVIE
            )

        val eval = MediaEquivalenceEvaluator.evaluate(tmdb, jikan)
        assertEquals(MediaEquivalenceClassification.INVALID_CANDIDATE, eval.classification)
    }

    @Test
    fun case25_inputOrderPermutation_deterministicResult() {
        val tmdb =
            CandidateMediaProjection(
                identity = tmdbMovieRef,
                title = "Spirited Away",
                releaseYear = 2001,
                imdbId = "tt0245429"
            )
        val jikan =
            CandidateMediaProjection(
                identity = jikanMovieRef,
                title = "Sen to Chihiro",
                releaseYear = 2001,
                animeFormat = AnimeFormat.MOVIE,
                imdbId = "tt0245429"
            )

        val eval1 = MediaEquivalenceEvaluator.evaluate(tmdb, jikan)
        val eval2 = MediaEquivalenceEvaluator.evaluate(jikan, tmdb)

        assertEquals(eval1.classification, eval2.classification)
        assertEquals(eval1.positiveSignals, eval2.positiveSignals)
    }
}
