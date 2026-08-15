package com.cydoniancitizen.bingee.data.library.local

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DetailsDaoTest {
    private lateinit var database: BingeeDatabase
    private lateinit var detailsDao: DetailsDao
    private lateinit var libraryDao: LibraryDao
    private val now = Instant.parse("2026-08-03T12:00:00Z")

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BingeeDatabase::class.java).build()
        detailsDao = database.detailsDao()
        libraryDao = database.libraryDao()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun cacheWriteCreatesNonMemberCanonicalIdentityAndOrdersGenres() = runBlocking {
        store(title = "Cached", genres = listOf("Second", "First"))

        val row = detailsDao.observeCachedDetails(MediaSource.TMDB, "550").first()
        assertNotNull(row)
        assertEquals("Cached", row?.media?.title)
        assertEquals(listOf("Second", "First"), row?.genres?.sortedBy { it.genreOrder }?.map { it.name })
        assertFalse(libraryDao.isInLibrary(MediaSource.TMDB, "550"))
    }

    @Test
    fun refreshPreservesMembershipAddedAtAndRemovalRetainsDetails() = runBlocking {
        val addedAt = now.minusSeconds(3600)
        libraryDao.addToLibrary(media("Search title", addedAt), MediaSource.TMDB, "550", addedAt)

        store(title = "Detailed title", fetchedAt = now)
        val membership = libraryDao.observeLibraryItem(MediaSource.TMDB, "550").first()
        assertEquals(addedAt, membership?.addedAt)
        assertEquals("Detailed title", membership?.media?.title)

        libraryDao.removeMembership(MediaSource.TMDB, "550")
        assertFalse(libraryDao.isInLibrary(MediaSource.TMDB, "550"))
        assertNotNull(detailsDao.getCachedDetails(MediaSource.TMDB, "550")?.details)
    }

    @Test
    fun refreshPreservesFavoriteAndOtherPersonalState() = runBlocking {
        val addedAt = now.minusSeconds(3600)
        libraryDao.addToLibrary(media("Search title", addedAt), MediaSource.TMDB, "550", addedAt)
        libraryDao.updateFavoriteState(MediaSource.TMDB, "550", true)
        val mediaId = libraryDao.getMediaByExternalRef(MediaSource.TMDB, "550")!!.localMediaId
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO media_ratings(local_media_id, rating_value, rated_at, updated_at) " +
                "VALUES($mediaId, 8, '2026-08-01T00:00:00Z', '2026-08-01T00:00:00Z')"
        )

        store(title = "Refreshed", fetchedAt = now.plusSeconds(60))

        assertEquals(true, detailsDao.getCachedDetails(MediaSource.TMDB, "550")?.media?.isFavorite)
        assertEquals(addedAt, libraryDao.observeLibraryItem(MediaSource.TMDB, "550").first()?.addedAt)
        assertEquals(1, count("media_ratings"))
    }

    @Test
    fun refreshKeepsFalseFavoriteFalse() = runBlocking {
        store(title = "First")
        store(title = "Refreshed", fetchedAt = now.plusSeconds(60))

        assertFalse(detailsDao.getCachedDetails(MediaSource.TMDB, "550")!!.media.isFavorite)
    }

    @Test
    fun repeatedWriteIsIdempotentAndReplacesGenresAtomically() = runBlocking {
        store(title = "First", genres = listOf("Old"))
        store(title = "Second", genres = listOf("New", "Other"), fetchedAt = now.plusSeconds(60))

        val row = detailsDao.getCachedDetails(MediaSource.TMDB, "550")
        assertEquals("Second", row?.media?.title)
        assertEquals(now.plusSeconds(60), row?.details?.detailsFetchedAt)
        assertEquals(listOf("New", "Other"), row?.genres?.sortedBy { it.genreOrder }?.map { it.name })
        assertEquals(1, count("media_entries"))
        assertEquals(1, count("external_refs"))
        assertEquals(1, count("media_details"))
    }

    @Test
    fun failedGenreReplacementRollsBackAllMetadata() = runBlocking {
        store(title = "Old", genres = listOf("Stable"))
        try {
            detailsDao.storeDetails(
                candidate = media("New", now.plusSeconds(60)),
                source = MediaSource.TMDB,
                externalId = "550",
                details = details(now.plusSeconds(60)),
                genres = listOf(
                    MediaGenreEntity(0, 0, "Duplicate A"),
                    MediaGenreEntity(0, 0, "Duplicate B")
                )
            )
            throw AssertionError("Expected duplicate genre order to fail")
        } catch (_: SQLiteConstraintException) {
            // Expected: Room transaction must roll back canonical metadata, detail row, and genres.
        }

        val row = detailsDao.getCachedDetails(MediaSource.TMDB, "550")
        assertEquals("Old", row?.media?.title)
        assertEquals(now, row?.details?.detailsFetchedAt)
        assertEquals(listOf("Stable"), row?.genres?.map { it.name })
    }

    @Test
    fun sameNumericIdAcrossProvidersRemainsSeparated() = runBlocking {
        store(title = "TMDB", source = MediaSource.TMDB)
        store(title = "IMDB", source = MediaSource.IMDB)

        assertEquals("TMDB", detailsDao.getCachedDetails(MediaSource.TMDB, "550")?.media?.title)
        assertEquals("IMDB", detailsDao.getCachedDetails(MediaSource.IMDB, "550")?.media?.title)
        assertEquals(2, count("media_entries"))
    }

    private suspend fun store(
        title: String,
        genres: List<String> = emptyList(),
        fetchedAt: Instant = now,
        source: MediaSource = MediaSource.TMDB
    ) {
        detailsDao.storeDetails(
            candidate = media(title, fetchedAt),
            source = source,
            externalId = "550",
            details = details(fetchedAt),
            genres = genres.mapIndexed { index, name -> MediaGenreEntity(0, index, name) }
        )
    }

    private fun media(title: String, timestamp: Instant) = MediaEntity(
        mediaType = MediaType.MOVIE,
        title = title,
        originalTitle = null,
        overview = "Overview",
        posterUrl = null,
        releaseDate = LocalDate.of(2020, 1, 1),
        createdAt = timestamp,
        metadataUpdatedAt = timestamp
    )

    private fun details(timestamp: Instant) = MediaDetailsEntity(
        localMediaId = 0,
        backdropUrl = null,
        productionStatus = "RELEASED",
        originalLanguage = "en",
        runtimeMinutes = 100,
        episodeRuntimeMinutes = null,
        numberOfSeasons = null,
        numberOfEpisodes = null,
        detailsFetchedAt = timestamp
    )

    private fun count(table: String): Int = database.openHelper.readableDatabase
        .query("SELECT COUNT(*) FROM $table")
        .use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
}
