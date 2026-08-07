package com.cydoniancitizen.bingee.data.library.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cydoniancitizen.bingee.core.model.MediaSource
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RatingDaoTest {
    private lateinit var database: BingeeDatabase
    private lateinit var dao: RatingDao
    private val first = Instant.parse("2026-08-03T10:00:00Z")
    private val second = Instant.parse("2026-08-03T11:00:00Z")

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BingeeDatabase::class.java).build()
        dao = database.ratingDao()
        insertMedia(1, "MOVIE", "101")
        insertMedia(2, "SERIES", "202")
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun firstUpdateIdenticalAndRemoveFollowTimestampPolicy() = runBlocking {
        assertNull(dao.observeRating(MediaSource.TMDB, "101").first())
        assertEquals(RatingWriteOutcome.SUCCESS, dao.setRating(MediaSource.TMDB, "101", 1, first))
        val initial = dao.observeRating(MediaSource.TMDB, "101").first()!!
        assertEquals(1, initial.ratingValue)
        assertEquals(first, initial.ratedAt)
        assertEquals(first, initial.updatedAt)

        assertEquals(RatingWriteOutcome.SUCCESS, dao.setRating(MediaSource.TMDB, "101", 10, second))
        assertEquals(RatingWriteOutcome.UNCHANGED, dao.setRating(MediaSource.TMDB, "101", 10, second.plusSeconds(60)))
        val updated = dao.observeRating(MediaSource.TMDB, "101").first()!!
        assertEquals(10, updated.ratingValue)
        assertEquals(first, updated.ratedAt)
        assertEquals(second, updated.updatedAt)

        assertEquals(RatingWriteOutcome.SUCCESS, dao.removeRating(MediaSource.TMDB, "101"))
        assertEquals(RatingWriteOutcome.UNCHANGED, dao.removeRating(MediaSource.TMDB, "101"))
        assertNull(dao.observeRating(MediaSource.TMDB, "101").first())
    }

    @Test
    fun movieAndTvRatingsAreProviderAwareAndSurviveLibraryRemoval() = runBlocking {
        dao.setRating(MediaSource.TMDB, "101", 7, first)
        dao.setRating(MediaSource.TMDB, "202", 8, first)
        database.libraryDao().removeMembership(MediaSource.TMDB, "101")

        assertEquals(7, dao.observeRating(MediaSource.TMDB, "101").first()!!.ratingValue)
        assertEquals(8, dao.observeRating(MediaSource.TMDB, "202").first()!!.ratingValue)
        assertEquals(RatingWriteOutcome.NOT_FOUND, dao.setRating(MediaSource.IMDB, "101", 5, first))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidRatingNeverReachesRoom(): Unit = runBlocking {
        dao.setRating(MediaSource.TMDB, "101", 0, first)
        Unit
    }

    private fun insertMedia(localId: Long, mediaType: String, externalId: String) {
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO media_entries(local_media_id, media_type, title, original_title, overview, " +
                "poster_url, release_date, created_at, metadata_updated_at, is_favorite) VALUES" +
                "($localId, '$mediaType', 'Title $localId', NULL, NULL, NULL, NULL, " +
                "'2026-08-03T09:00:00Z', '2026-08-03T09:00:00Z', 0)"
        )
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO external_refs(local_media_id, source, external_id) VALUES($localId, 'TMDB', '$externalId')"
        )
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO library_entries(local_media_id, added_at) VALUES($localId, '2026-08-03T09:00:00Z')"
        )
    }
}
