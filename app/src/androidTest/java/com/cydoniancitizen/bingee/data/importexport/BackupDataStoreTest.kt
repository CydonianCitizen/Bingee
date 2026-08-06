package com.cydoniancitizen.bingee.data.importexport

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cydoniancitizen.bingee.core.model.AnimeCompletionOrigin
import com.cydoniancitizen.bingee.core.model.AnimeFormat
import com.cydoniancitizen.bingee.core.model.AnimeStatus
import com.cydoniancitizen.bingee.core.model.MediaLinkAuditAction
import com.cydoniancitizen.bingee.core.model.MediaLinkAuditOrigin
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.ReleaseEventType
import com.cydoniancitizen.bingee.data.library.local.BingeeDatabase
import com.cydoniancitizen.bingee.data.settings.DataStoreReleaseNotificationPreferences
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupDataStoreTest {
    private lateinit var database: BingeeDatabase
    private lateinit var store: BackupDataStore
    private val exportedAt = Instant.parse("2026-08-04T10:00:00Z")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            BingeeDatabase::class.java
        ).build()
        store = BackupDataStore(
            database,
            database.portableSnapshotDao(),
            database.releaseEventDao(),
            DataStoreReleaseNotificationPreferences(
                ApplicationProvider.getApplicationContext(),
                database,
                database.portableSnapshotDao()
            )
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun restoreReplacesPortableStateRegeneratesIdsAndSupportsRepeatImport() = runBlocking {
        val first = plan("1", includeSeries = false)
        store.restore(first)
        val firstId = database.portableSnapshotDao().readSnapshot().media.single().localMediaId

        val second = plan("2", includeSeries = true)
        store.restore(second)
        val secondSnapshot = database.portableSnapshotDao().readSnapshot()
        assertEquals(2, secondSnapshot.media.size)
        assertNotEquals(firstId, secondSnapshot.media.single { it.mediaType == MediaType.MOVIE }.localMediaId)
        assertEquals(1, secondSnapshot.memberships.size)
        assertEquals(1, secondSnapshot.movieProgress.size)
        assertEquals(1, secondSnapshot.episodeProgress.size)
        assertEquals(1, secondSnapshot.ratings.size)
        assertEquals(1, secondSnapshot.seasons.size)
        assertEquals(1, secondSnapshot.episodes.size)

        store.restore(second)
        val repeated = database.portableSnapshotDao().readSnapshot()
        assertEquals(secondSnapshot.media.size, repeated.media.size)
        assertEquals(secondSnapshot.refs.size, repeated.refs.size)
        assertEquals(secondSnapshot.memberships.size, repeated.memberships.size)
        assertEquals(secondSnapshot.episodeProgress.size, repeated.episodeProgress.size)
    }

    @Test
    fun v1RestoreCreatesNoAnimeRowsAndPreservesSemanticRoundTrip() = runBlocking {
        val v1 = plan(
            movieId = "v1",
            includeSeries = true,
            schemaVersion = BACKUP_SCHEMA_VERSION_V1
        )

        val parsed = BackupJsonCodec.parse(BackupJsonCodec.encode(v1.document))
            as BackupParseResult.Success
        assertEquals(BACKUP_SCHEMA_VERSION_V1, parsed.document.schemaVersion)

        store.restore(v1)
        val snapshot = database.portableSnapshotDao().readSnapshot()
        assertTrue(snapshot.animeDetails.isEmpty())
        assertTrue(snapshot.animeRelations.isEmpty())
        assertTrue(snapshot.animeProgress.isEmpty())

        val clock = Clock.fixed(exportedAt, ZoneOffset.UTC)
        val first = exportedDocument(BackupExporter(store, clock).export().bytes)
        assertTrue(first.data.animeDetails.isEmpty())
        assertTrue(first.data.animeRelations.isEmpty())
        assertTrue(first.data.animeProgress.isEmpty())

        store.restore((BackupValidator.validate(first) as BackupValidationResult.Success).plan)
        val second = exportedDocument(BackupExporter(store, clock).export().bytes)
        assertEquals(first.data, second.data)
    }

    @Test
    fun restoreRollsBackAtEveryInsertionStage() = runBlocking {
        store.restore(plan("existing", includeSeries = true))
        val before = database.portableSnapshotDao().readSnapshot()

        RestoreStage.entries.forEach { failedStage ->
            var failed = false
            try {
                store.restore(
                    plan(
                        "incoming-${failedStage.name}",
                        includeSeries = true,
                        includeAnime = true
                    ),
                    RestoreFailureInjector { stage ->

                        if (stage == failedStage) throw InjectedRestoreFailure(stage)
                    }
                )
            } catch (_: InjectedRestoreFailure) {
                failed = true
            }
            assertTrue("restore must fail at ${failedStage.name}", failed)
            assertEquals(before, database.portableSnapshotDao().readSnapshot())
        }
    }

    @Test
    fun versionTwoRestoresAnimeProgressRatingRelationsAndPremiere() = runBlocking {
        val animeRef = BackupRef(MediaSource.JIKAN, "42")
        val data = BackupData(
            media = listOf(
                BackupMedia(
                    primaryRef = animeRef,
                    externalRefs = listOf(animeRef),
                    mediaType = MediaType.ANIME,
                    title = "Anime",
                    originalTitle = "アニメ",
                    overview = null,
                    posterUrl = null,
                    releaseDate = null
                )
            ),
            seasons = emptyList(),
            episodes = emptyList(),
            library = listOf(BackupLibraryEntry(animeRef, exportedAt)),
            movieProgress = emptyList(),
            episodeProgress = emptyList(),
            ratings = listOf(BackupRating(animeRef, 9, exportedAt, exportedAt)),
            preferences = BackupPreferences(1, true, true, true),
            animeDetails = listOf(
                BackupAnimeDetails(
                    animeRef,
                    AnimeFormat.TV,
                    AnimeStatus.FINISHED,
                    "Anime",
                    "アニメ",
                    "Cached",
                    12,
                    "24 min",
                    LocalDate.of(2026, 8, 5),
                    null,
                    "summer",
                    2026,
                    8.5,
                    null
                )
            ),
            animeRelations = listOf(
                BackupAnimeRelation(
                    animeRef,
                    "Sequel",
                    BackupRef(MediaSource.JIKAN, "43"),
                    "Anime 2",
                    AnimeFormat.TV
                )
            ),
            animeProgress = listOf(
                BackupAnimeProgress(
                    animeRef,
                    12,
                    exportedAt,
                    AnimeCompletionOrigin.EXPLICIT,
                    exportedAt
                )
            )
        )
        val document = BackupDocument(BACKUP_FORMAT_ID, BACKUP_SCHEMA_VERSION, exportedAt, data)
        val plan = (BackupValidator.validate(document) as BackupValidationResult.Success).plan

        store.restore(plan)

        val snapshot = database.portableSnapshotDao().readSnapshot()
        assertEquals(1, snapshot.animeDetails.size)
        assertEquals(1, snapshot.animeRelations.size)
        assertEquals(1, snapshot.animeProgress.size)
        assertEquals(1, snapshot.ratings.size)
        val events = database.releaseEventDao().getActiveEventsBetween(
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 31),
            10
        )
        assertEquals(ReleaseEventType.ANIME_PREMIERE, events.single().eventType)
    }

    @Test
    fun exportedBackupRestoresToTheSameSemanticData() = runBlocking {
        store.restore(roundTripPlan())
        val clock = Clock.fixed(exportedAt, ZoneOffset.UTC)
        val first = exportedDocument(BackupExporter(store, clock).export().bytes)

        val removedAnime = BackupRef(MediaSource.JIKAN, "900001")
        assertTrue(first.data.library.none { it.mediaRef == removedAnime })
        assertTrue(first.data.animeDetails.any { it.mediaRef == removedAnime })
        assertEquals(5, first.data.animeProgress.single { it.mediaRef == removedAnime }.watchedEpisodeCount)
        assertEquals(6, first.data.ratings.single { it.mediaRef == removedAnime }.rating)

        val validated = BackupValidator.validate(first)
        assertTrue(validated is BackupValidationResult.Success)
        store.restore((validated as BackupValidationResult.Success).plan)

        val second = exportedDocument(BackupExporter(store, clock).export().bytes)
        assertEquals(first.data, second.data)
    }

    private fun exportedDocument(bytes: ByteArray): BackupDocument =
        (BackupJsonCodec.parse(bytes) as BackupParseResult.Success).document

    private class InjectedRestoreFailure(stage: RestoreStage) : RuntimeException(stage.name)

    private fun roundTripPlan(): ValidatedBackupPlan {
        val movie = BackupRef(MediaSource.TMDB, "movie-round-trip")
        val movieAlias = BackupRef(MediaSource.TMDB, "movie-round-trip-alias")
        val series = BackupRef(MediaSource.TMDB, "series-round-trip")
        val seriesAlias = BackupRef(MediaSource.TMDB, "series-round-trip-alias")
        val specials = BackupRef(MediaSource.TMDB, "season-round-trip-specials")
        val regular = BackupRef(MediaSource.TMDB, "season-round-trip-1")
        val watchedEpisode = BackupRef(MediaSource.TMDB, "episode-round-trip-watched")
        val futureEpisode = BackupRef(MediaSource.TMDB, "episode-round-trip-future")
        val removedMovie = BackupRef(MediaSource.TMDB, "movie-round-trip-removed")
        val removedAnime = BackupRef(MediaSource.JIKAN, "900001")
        val watchedAt = Instant.parse("2026-07-01T08:00:00Z")
        val ratedAt = Instant.parse("2026-07-02T08:00:00Z")
        val data = BackupData(
            media = listOf(
                BackupMedia(
                    movie,
                    listOf(movie, movieAlias),
                    MediaType.MOVIE,
                    "映画 — 日本語",
                    null,
                    null,
                    null,
                    null
                ),
                BackupMedia(
                    series,
                    listOf(series, seriesAlias),
                    MediaType.SERIES,
                    "Series with optional metadata",
                    null,
                    null,
                    null,
                    null
                ),
                BackupMedia(
                    removedMovie,
                    listOf(removedMovie),
                    MediaType.MOVIE,
                    "Removed but rated",
                    null,
                    null,
                    null,
                    null
                ),
                BackupMedia(
                    removedAnime,
                    listOf(removedAnime),
                    MediaType.ANIME,
                    "Removed Anime",
                    "削除したアニメ",
                    "Portable anime metadata",
                    null,
                    LocalDate.of(2026, 6, 1)
                )
            ),
            seasons = listOf(
                BackupSeason(series, specials, 0, "Specials", null, null, null, 1),
                BackupSeason(series, regular, 1, "Season 1", null, null, null, 2)
            ),
            episodes = listOf(
                BackupEpisode(specials, watchedEpisode, 1, "Special", null, null, 42, null),
                BackupEpisode(
                    regular,
                    futureEpisode,
                    1,
                    "Future episode",
                    null,
                    java.time.LocalDate.of(2026, 12, 31),
                    null,
                    null
                )
            ),
            library = listOf(
                BackupLibraryEntry(movie, exportedAt),
                BackupLibraryEntry(series, exportedAt)
            ),
            movieProgress = listOf(
                BackupMovieProgress(movie, watchedAt),
                BackupMovieProgress(removedMovie, watchedAt)
            ),
            episodeProgress = listOf(BackupEpisodeProgress(watchedEpisode, watchedAt)),
            ratings = listOf(
                BackupRating(movie, 10, ratedAt, ratedAt),
                BackupRating(removedMovie, 4, ratedAt, ratedAt),
                BackupRating(removedAnime, 6, ratedAt, ratedAt)
            ),
            preferences = BackupPreferences(7, false, true, false),
            animeDetails = listOf(
                BackupAnimeDetails(
                    mediaRef = removedAnime,
                    format = AnimeFormat.TV,
                    status = AnimeStatus.FINISHED,
                    englishTitle = "Removed Anime",
                    japaneseTitle = "削除したアニメ",
                    synopsis = "Portable anime metadata",
                    episodeCount = 12,
                    duration = "24 min",
                    startDate = LocalDate.of(2026, 6, 1),
                    endDate = null,
                    season = "spring",
                    year = 2026,
                    providerScore = 7.0,
                    posterUrl = null
                )
            ),
            animeProgress = listOf(
                BackupAnimeProgress(
                    mediaRef = removedAnime,
                    watchedEpisodeCount = 5,
                    completedAt = null,
                    completionOrigin = null,
                    updatedAt = watchedAt
                )
            )
        )
        val document = BackupDocument(BACKUP_FORMAT_ID, BACKUP_SCHEMA_VERSION, exportedAt, data)
        return (BackupValidator.validate(document) as BackupValidationResult.Success).plan
    }

    private fun plan(
        movieId: String,
        includeSeries: Boolean,
        schemaVersion: Int = BACKUP_SCHEMA_VERSION,
        includeAnime: Boolean = false
    ): ValidatedBackupPlan {
        val movieRef = BackupRef(MediaSource.TMDB, movieId)
        val seriesRef = BackupRef(MediaSource.TMDB, "series-$movieId")
        val seasonRef = BackupRef(MediaSource.TMDB, "season-$movieId")
        val episodeRef = BackupRef(MediaSource.TMDB, "episode-$movieId")
        val animeRef = BackupRef(MediaSource.JIKAN, "7001")
        val data = BackupData(
            media = buildList {
                add(BackupMedia(movieRef, listOf(movieRef), MediaType.MOVIE, "Movie $movieId", null, null, null, null))
                if (includeSeries) {
                    add(
                        BackupMedia(
                            seriesRef,
                            listOf(seriesRef),
                            MediaType.SERIES,
                            "Series $movieId",
                            null,
                            null,
                            null,
                            null
                        )
                    )
                }
                if (includeAnime) {
                    add(
                        BackupMedia(
                            animeRef,
                            listOf(animeRef),
                            MediaType.ANIME,
                            "Anime $movieId",
                            "アニメ $movieId",
                            "Portable anime synopsis",
                            null,
                            null
                        )
                    )
                }
            },
            seasons = if (includeSeries) {
                listOf(
                    BackupSeason(seriesRef, seasonRef, 0, "Specials", null, null, null, 1)
                )
            } else {
                emptyList()
            },
            episodes = if (includeSeries) {
                listOf(
                    BackupEpisode(seasonRef, episodeRef, 1, "Episode", null, null, 40, null)
                )
            } else {
                emptyList()
            },
            library = buildList {
                add(BackupLibraryEntry(movieRef, exportedAt))
                if (includeAnime) add(BackupLibraryEntry(animeRef, exportedAt))
            },
            movieProgress = listOf(BackupMovieProgress(movieRef, exportedAt)),
            episodeProgress = if (includeSeries) listOf(BackupEpisodeProgress(episodeRef, exportedAt)) else emptyList(),
            ratings = buildList {
                add(BackupRating(movieRef, 9, exportedAt, exportedAt))
                if (includeAnime) add(BackupRating(animeRef, 8, exportedAt, exportedAt))
            },
            preferences = BackupPreferences(3, true, false, true),
            animeDetails = if (includeAnime) {
                listOf(
                    BackupAnimeDetails(
                        mediaRef = animeRef,
                        format = AnimeFormat.TV,
                        status = AnimeStatus.FINISHED,
                        englishTitle = "Anime $movieId",
                        japaneseTitle = "アニメ $movieId",
                        synopsis = "Portable anime synopsis",
                        episodeCount = 2,
                        duration = "24 min",
                        startDate = LocalDate.of(2026, 1, 1),
                        endDate = LocalDate.of(2026, 1, 15),
                        season = "winter",
                        year = 2026,
                        providerScore = 8.0,
                        posterUrl = null
                    )
                )
            } else {
                emptyList()
            },
            animeRelations = if (includeAnime) {
                listOf(
                    BackupAnimeRelation(
                        mediaRef = animeRef,
                        relationType = "Sequel",
                        relatedRef = BackupRef(MediaSource.JIKAN, "7002"),
                        relatedTitle = "Anime sequel",
                        relatedFormat = AnimeFormat.TV
                    )
                )
            } else {
                emptyList()
            },
            animeProgress = if (includeAnime) {
                listOf(
                    BackupAnimeProgress(
                        mediaRef = animeRef,
                        watchedEpisodeCount = 1,
                        completedAt = null,
                        completionOrigin = null,
                        updatedAt = exportedAt
                    )
                )
            } else {
                emptyList()
            },
            mediaLinkGroups = if (includeAnime && includeSeries) {
                listOf(
                    BackupMediaLinkGroup(
                        groupId = "group-$movieId",
                        preferredPresentation = BackupMediaIdentity(MediaSource.TMDB, MediaType.MOVIE, movieId),
                        createdAt = exportedAt,
                        updatedAt = exportedAt,
                        members = listOf(
                            BackupMediaIdentity(MediaSource.TMDB, MediaType.MOVIE, movieId),
                            BackupMediaIdentity(MediaSource.JIKAN, MediaType.ANIME, "7001")
                        )
                    )
                )
            } else {
                emptyList()
            },
            mediaLinkAudit = if (includeAnime && includeSeries) {
                listOf(
                    BackupMediaLinkAudit(
                        groupId = "group-$movieId",
                        action = MediaLinkAuditAction.LINKED,
                        timestamp = exportedAt,
                        origin = MediaLinkAuditOrigin.MANUAL_USER_ACTION,
                        members = listOf(
                            BackupMediaIdentity(MediaSource.TMDB, MediaType.MOVIE, movieId),
                            BackupMediaIdentity(MediaSource.JIKAN, MediaType.ANIME, "7001")
                        ),
                        preferredPresentation = BackupMediaIdentity(MediaSource.TMDB, MediaType.MOVIE, movieId)
                    )
                )
            } else {
                emptyList()
            }
        )
        val document = BackupDocument(BACKUP_FORMAT_ID, schemaVersion, exportedAt, data)
        return (BackupValidator.validate(document) as BackupValidationResult.Success).plan
    }
}
