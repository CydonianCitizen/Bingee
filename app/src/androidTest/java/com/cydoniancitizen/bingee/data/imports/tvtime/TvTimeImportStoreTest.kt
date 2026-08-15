package com.cydoniancitizen.bingee.data.imports.tvtime

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cydoniancitizen.bingee.core.model.Episode
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaDetails
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.Season
import com.cydoniancitizen.bingee.data.calendar.ReleaseEventProjector
import com.cydoniancitizen.bingee.data.imports.model.ImportedIdentityNamespace
import com.cydoniancitizen.bingee.data.imports.model.ImportedSourceIdentity
import com.cydoniancitizen.bingee.data.library.local.BingeeDatabase
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TvTimeImportStoreTest {
    private lateinit var database: BingeeDatabase
    private lateinit var store: TvTimeImportStore
    private val createdAt = Instant.parse("2024-01-01T00:00:00Z")
    private val watchedAt = Instant.parse("2024-02-03T04:05:06Z")

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BingeeDatabase::class.java).build()
        store = TvTimeImportStore(
            database = database,
            libraryDao = database.libraryDao(),
            detailsDao = database.detailsDao(),
            seriesDao = database.seriesDao(),
            importProgressDao = database.importProgressDao(),
            provenanceDao = database.importProvenanceDao(),
            releaseEventDao = database.releaseEventDao(),
            projector = ReleaseEventProjector()
        )
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun repeatedImportAddsOnceAndPreservesExistingTimestampAndRating() = runBlocking {
        val plan = moviePlan("movie-uuid", "Imported Movie", "101", watchedAt)

        val first = store.import(plan)
        assertTrue(first is com.cydoniancitizen.bingee.core.result.AppResult.Success)
        assertEquals(1, count("media_entries"))
        assertEquals(1, count("library_entries"))
        assertEquals(1, count("movie_watch_progress"))
        assertEquals(3, count("import_provenance_refs"))

        val mediaId = long("SELECT local_media_id FROM media_entries LIMIT 1")
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO media_ratings(local_media_id, rating_value, rated_at, updated_at) " +
                "VALUES($mediaId, 8, '2024-03-01T00:00:00Z', '2024-03-01T00:00:00Z')"
        )
        val existingAddedAt = text("SELECT added_at FROM library_entries LIMIT 1")
        val existingWatchedAt = text("SELECT watched_at FROM movie_watch_progress LIMIT 1")
        database.libraryDao().updateFavoriteState(MediaSource.TMDB, "101", true)

        val second = store.import(plan.copy(confirmedAt = Instant.parse("2026-01-01T00:00:00Z")))
        assertTrue(second is com.cydoniancitizen.bingee.core.result.AppResult.Success)
        val report = (second as com.cydoniancitizen.bingee.core.result.AppResult.Success).value
        assertEquals(0, report.movieProgressAdded)
        assertEquals(1, report.movieProgressPreserved)
        assertEquals(existingAddedAt, text("SELECT added_at FROM library_entries LIMIT 1"))
        assertEquals(existingWatchedAt, text("SELECT watched_at FROM movie_watch_progress LIMIT 1"))
        assertEquals(1, count("media_entries"))
        assertEquals(1, count("library_entries"))
        assertEquals(1, count("movie_watch_progress"))
        assertEquals(1, count("media_ratings"))
        assertEquals(3, count("import_provenance_refs"))
        assertEquals("1", text("SELECT is_favorite FROM media_entries LIMIT 1"))
    }

    @Test
    fun provenanceConflictRollsBackAllEarlierImportRows() = runBlocking {
        val first = movieChange("first-uuid", "First", "101")
        val conflicting = movieChange("second-uuid", "Second", "102").copy(
            source = movieChange("second-uuid", "Second", "102").source.copy(
                identities = listOf(ImportedSourceIdentity(ImportedIdentityNamespace.IMDB, "tt-shared"))
            )
        )
        val firstWithConflict = first.copy(
            source = first.source.copy(
                identities = listOf(ImportedSourceIdentity(ImportedIdentityNamespace.IMDB, "tt-shared"))
            )
        )
        val result = store.import(
            TvTimeImportPlan(
                profileId = TV_TIME_PROFILE_ID,
                confirmedAt = Instant.parse("2026-01-01T00:00:00Z"),
                media = listOf(firstWithConflict, conflicting),
                episodes = emptyList(),
                skippedRecordIds = emptyList(),
                invalidRecordCount = 0,
                unsupported = com.cydoniancitizen.bingee.data.imports.model.ImportedUnsupportedFields()
            )
        )
        assertTrue(result is com.cydoniancitizen.bingee.core.result.AppResult.Failure)
        assertEquals(0, count("media_entries"))
        assertEquals(0, count("external_refs"))
        assertEquals(0, count("library_entries"))
        assertEquals(0, count("import_provenance_refs"))
    }

    @Test
    fun seriesEpisodeSeasonZeroAndReleaseEventsAreAdditiveAndIdempotent() = runBlocking {
        val plan = fullPlan()
        val preview = store.preview(plan)
        assertEquals(2, preview.newLibraryCount)
        assertEquals(1, preview.movieProgressToAdd)
        assertEquals(1, preview.episodeProgressToAdd)

        val first = store.import(plan, preview)
        assertTrue(first is com.cydoniancitizen.bingee.core.result.AppResult.Success)
        assertEquals(2, count("media_entries"))
        assertEquals(2, count("library_entries"))
        assertEquals(1, count("seasons"))
        assertEquals(1, count("episodes"))
        assertEquals(1, count("movie_watch_progress"))
        assertEquals(1, count("episode_watch_progress"))
        assertEquals(3, count("release_events"))

        val secondPreview = store.preview(plan)
        val second = store.import(plan, secondPreview)
        assertTrue(second is com.cydoniancitizen.bingee.core.result.AppResult.Success)
        assertEquals(2, count("media_entries"))
        assertEquals(2, count("library_entries"))
        assertEquals(1, count("seasons"))
        assertEquals(1, count("episodes"))
        assertEquals(1, count("movie_watch_progress"))
        assertEquals(1, count("episode_watch_progress"))
        assertEquals(3, count("release_events"))
    }

    @Test
    fun everyLogicalWriteStageRollsBackCompletely() = runBlocking {
        TvTimeImportWriteStage.entries.forEach { failedStage ->
            var injected = false
            val result = store.import(
                plan = fullPlan(),
                failureInjector = TvTimeImportFailureInjector { stage ->
                    if (stage == failedStage) {
                        injected = true
                        throw InjectedImportFailure(failedStage)
                    }
                }
            )
            assertTrue("Stage was not reached: $failedStage", injected)
            assertTrue(result is com.cydoniancitizen.bingee.core.result.AppResult.Failure)
            listOf(
                "media_entries",
                "external_refs",
                "library_entries",
                "seasons",
                "episodes",
                "movie_watch_progress",
                "episode_watch_progress",
                "release_events",
                "import_provenance_refs"
            ).forEach { table -> assertEquals("Rollback failed for $failedStage at $table", 0, count(table)) }
        }
    }

    @Test
    fun stalePreviewRejectsTransactionWithoutChangingLocalState() = runBlocking {
        val plan = moviePlan("movie-uuid", "Imported Movie", "101", watchedAt)
        val preview = store.preview(plan)
        val first = store.import(plan)
        assertTrue(first is com.cydoniancitizen.bingee.core.result.AppResult.Success)
        val countsBefore = listOf(count("media_entries"), count("library_entries"), count("movie_watch_progress"))

        val stale = store.import(plan, preview)
        assertTrue(stale is com.cydoniancitizen.bingee.core.result.AppResult.Failure)
        assertEquals(
            countsBefore,
            listOf(count("media_entries"), count("library_entries"), count("movie_watch_progress"))
        )
    }

    @Test
    fun importLeavesPortablePreferencesRefreshStateAndDeliveryLedgerUntouched() = runBlocking {
        val sql = database.openHelper.writableDatabase
        sql.execSQL(
            "INSERT INTO portable_preferences VALUES(1, 7, 0, 1, 0, 1)"
        )
        sql.execSQL(
            "INSERT INTO calendar_refresh_state VALUES(1, '2024-04-01T00:00:00Z')"
        )
        sql.execSQL(
            "INSERT INTO notification_deliveries VALUES(" +
                "'TMDB','MEDIA','old','MOVIE_RELEASE','2024-05-01',1,42,'2024-04-30T00:00:00Z')"
        )

        val result = store.import(moviePlan("movie-uuid", "Imported Movie", "101", watchedAt))
        assertTrue(result is com.cydoniancitizen.bingee.core.result.AppResult.Success)
        assertEquals(1, count("portable_preferences"))
        assertEquals("7", text("SELECT notification_lead_days FROM portable_preferences"))
        assertEquals(1, count("calendar_refresh_state"))
        assertEquals(1, count("notification_deliveries"))
    }

    private fun moviePlan(uuid: String, title: String, id: String, at: Instant) = TvTimeImportPlan(
        profileId = TV_TIME_PROFILE_ID,
        confirmedAt = Instant.parse("2026-01-01T00:00:00Z"),
        media = listOf(movieChange(uuid, title, id, at)),
        episodes = emptyList(),
        skippedRecordIds = emptyList(),
        invalidRecordCount = 0,
        unsupported = com.cydoniancitizen.bingee.data.imports.model.ImportedUnsupportedFields()
    )

    private fun movieChange(uuid: String, title: String, id: String, at: Instant = watchedAt) = TvTimeMediaImportChange(
        source = TvTimeMediaImportSource(
            title = title,
            createdAt = createdAt,
            watchedAt = at,
            identities = listOf(
                ImportedSourceIdentity(ImportedIdentityNamespace.IMDB, "tt-$uuid"),
                ImportedSourceIdentity(ImportedIdentityNamespace.TVDB, id),
                ImportedSourceIdentity(ImportedIdentityNamespace.TV_TIME, uuid)
            )
        ),
        candidate = TmdbImportCandidate(
            ExternalMediaRef(MediaSource.TMDB, id),
            MediaType.MOVIE,
            title,
            null,
            2020,
            null,
            null
        ),
        details = MediaDetails(ExternalMediaRef(MediaSource.TMDB, id), MediaType.MOVIE, title),
        seasons = emptyList()
    )

    private fun fullPlan(): TvTimeImportPlan {
        val seriesRef = ExternalMediaRef(MediaSource.TMDB, "201")
        val seasonRef = ExternalMediaRef(MediaSource.TMDB, "202")
        val episodeRef = ExternalMediaRef(MediaSource.TMDB, "203")
        val season = Season(
            seriesRef = seriesRef,
            externalRef = seasonRef,
            seasonNumber = 0,
            name = "Specials",
            airDate = LocalDate.parse("2024-05-01"),
            episodeCount = 1
        )
        val episode = Episode(
            seriesRef = seriesRef,
            seasonRef = seasonRef,
            externalRef = episodeRef,
            seasonNumber = 0,
            episodeNumber = 1,
            title = "Special",
            airDate = LocalDate.parse("2024-05-02")
        )
        val series = TvTimeMediaImportChange(
            source = TvTimeMediaImportSource(
                title = "Imported Series",
                createdAt = createdAt,
                watchedAt = null,
                identities = listOf(
                    ImportedSourceIdentity(ImportedIdentityNamespace.TVDB, "9201"),
                    ImportedSourceIdentity(ImportedIdentityNamespace.TV_TIME, "series-uuid")
                )
            ),
            candidate = TmdbImportCandidate(seriesRef, MediaType.SERIES, "Imported Series", null, 2024, null, null),
            details = MediaDetails(seriesRef, MediaType.SERIES, "Imported Series"),
            seasons = listOf(season)
        )
        return TvTimeImportPlan(
            profileId = TV_TIME_PROFILE_ID,
            confirmedAt = Instant.parse("2026-01-01T00:00:00Z"),
            media = listOf(
                movieChange("movie-uuid", "Imported Movie", "101").copy(
                    details = MediaDetails(
                        ExternalMediaRef(MediaSource.TMDB, "101"),
                        MediaType.MOVIE,
                        "Imported Movie",
                        releaseDate = LocalDate.parse("2024-04-01")
                    )
                ),
                series
            ),
            episodes = listOf(
                TvTimeEpisodeImportChange(
                    source = TvTimeEpisodeImportSource(
                        title = "Special",
                        watchedAt = watchedAt,
                        identities = listOf(ImportedSourceIdentity(ImportedIdentityNamespace.TVDB, "9203"))
                    ),
                    episode = episode,
                    season = season
                )
            ),
            skippedRecordIds = emptyList(),
            invalidRecordCount = 0,
            unsupported = com.cydoniancitizen.bingee.data.imports.model.ImportedUnsupportedFields()
        )
    }

    private class InjectedImportFailure(stage: TvTimeImportWriteStage) : RuntimeException(stage.name)

    private fun count(table: String): Int =
        database.openHelper.writableDatabase.query("SELECT COUNT(*) FROM $table").use {
            it.moveToFirst()
            it.getInt(0)
        }

    private fun text(query: String): String = database.openHelper.writableDatabase.query(query).use {
        it.moveToFirst()
        it.getString(0)
    }

    private fun long(query: String): Long = database.openHelper.writableDatabase.query(query).use {
        it.moveToFirst()
        it.getLong(0)
    }
}
