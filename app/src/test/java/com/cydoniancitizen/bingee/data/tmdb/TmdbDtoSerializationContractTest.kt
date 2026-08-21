package com.cydoniancitizen.bingee.data.tmdb

import com.cydoniancitizen.bingee.data.tmdb.auth.TmdbAuthenticationResponse
import com.cydoniancitizen.bingee.data.tmdb.details.TmdbGenreDto
import com.cydoniancitizen.bingee.data.tmdb.details.TmdbMovieDetailsDto
import com.cydoniancitizen.bingee.data.tmdb.details.TmdbSeasonSummaryDto
import com.cydoniancitizen.bingee.data.tmdb.details.TmdbTvDetailsDto
import com.cydoniancitizen.bingee.data.tmdb.search.TmdbFindEpisodeDto
import com.cydoniancitizen.bingee.data.tmdb.search.TmdbFindMovieDto
import com.cydoniancitizen.bingee.data.tmdb.search.TmdbFindResponseDto
import com.cydoniancitizen.bingee.data.tmdb.search.TmdbFindTvDto
import com.cydoniancitizen.bingee.data.tmdb.search.TmdbMovieSearchResponseDto
import com.cydoniancitizen.bingee.data.tmdb.search.TmdbMovieSearchResultDto
import com.cydoniancitizen.bingee.data.tmdb.search.TmdbTvSearchResponseDto
import com.cydoniancitizen.bingee.data.tmdb.search.TmdbTvSearchResultDto
import com.cydoniancitizen.bingee.data.tmdb.series.TmdbEpisodeDto
import com.cydoniancitizen.bingee.data.tmdb.series.TmdbSeasonDetailsDto
import com.google.gson.annotations.SerializedName
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Release builds run these payload classes through R8, which renames fields that carry no explicit
 * JSON name. Gson then matches nothing and every value arrives null, which once turned a valid TMDB
 * credential into "could not be verified" in the shipped build while debug stayed green.
 */
class TmdbDtoSerializationContractTest {
    private val payloadClasses = listOf(
        TmdbAuthenticationResponse::class.java,
        TmdbMovieDetailsDto::class.java,
        TmdbTvDetailsDto::class.java,
        TmdbGenreDto::class.java,
        TmdbSeasonSummaryDto::class.java,
        TmdbSeasonDetailsDto::class.java,
        TmdbEpisodeDto::class.java,
        TmdbFindResponseDto::class.java,
        TmdbFindMovieDto::class.java,
        TmdbFindTvDto::class.java,
        TmdbFindEpisodeDto::class.java,
        TmdbMovieSearchResponseDto::class.java,
        TmdbMovieSearchResultDto::class.java,
        TmdbTvSearchResponseDto::class.java,
        TmdbTvSearchResultDto::class.java
    )

    @Test
    fun everyTmdbPayloadFieldDeclaresItsJsonName() {
        val unannotated = payloadClasses.flatMap { type ->
            type.declaredFields
                // Gson only reads instance fields; static ones such as the Compose `$stable` marker
                // are never part of a payload.
                .filterNot { it.isSynthetic || Modifier.isStatic(it.modifiers) }
                .filter { it.getAnnotation(SerializedName::class.java) == null }
                .map { "${type.simpleName}.${it.name}" }
        }

        assertEquals(emptyList<String>(), unannotated)
    }
}
