package com.cydoniancitizen.bingee.data.rating

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.PersonalRating
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.library.local.BingeeDatabase
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DefaultRatingRepositoryTest {
    private lateinit var database: BingeeDatabase
    private lateinit var repository: DefaultRatingRepository
    private val reference = ExternalMediaRef(MediaSource.TMDB, "101")

    @Before
    fun createRepository() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BingeeDatabase::class.java).build()
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO media_entries(local_media_id, media_type, title, original_title, overview, poster_url, " +
                "release_date, created_at, metadata_updated_at) VALUES" +
                "(1, 'MOVIE', 'Movie', NULL, NULL, NULL, NULL, '2026-08-03T09:00:00Z', '2026-08-03T09:00:00Z')"
        )
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO external_refs(local_media_id, source, external_id) VALUES(1, 'TMDB', '101')"
        )
        repository = DefaultRatingRepository(
            database.ratingDao(),
            Clock.fixed(Instant.parse("2026-08-03T10:00:00Z"), ZoneOffset.UTC)
        )
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun observeSetUpdateAndRemoveNeedNoMembershipNetworkOrCredential() = runBlocking {
        assertEquals(AppResult.Success(null), repository.observeRating(reference).first())
        assertEquals(AppResult.Success(Unit), repository.setRating(reference, PersonalRating(1)))
        assertEquals(AppResult.Success(PersonalRating(1)), repository.observeRating(reference).first())
        assertEquals(AppResult.Success(Unit), repository.setRating(reference, PersonalRating(10)))
        assertEquals(AppResult.Success(Unit), repository.setRating(reference, PersonalRating(10)))
        assertEquals(AppResult.Success(Unit), repository.removeRating(reference))
        assertEquals(AppResult.Success(Unit), repository.removeRating(reference))
        assertEquals(AppResult.Success(null), repository.observeRating(reference).first())
    }

    @Test
    fun missingAndMalformedProviderIdentityMapToSafeErrors() = runBlocking {
        assertEquals(
            AppResult.Failure(AppError.MissingData),
            repository.setRating(ExternalMediaRef(MediaSource.TMDB, "missing"), PersonalRating(5))
        )
        assertEquals(
            AppResult.Failure(AppError.InvalidInput),
            repository.removeRating(ExternalMediaRef(MediaSource.TMDB, "   "))
        )
    }
}
