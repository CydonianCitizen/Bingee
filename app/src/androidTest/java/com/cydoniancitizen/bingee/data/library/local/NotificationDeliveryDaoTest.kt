package com.cydoniancitizen.bingee.data.library.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.ReleaseEventType
import com.cydoniancitizen.bingee.core.model.ReleaseSubjectType
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationDeliveryDaoTest {
    private lateinit var database: BingeeDatabase
    private lateinit var dao: NotificationDeliveryDao

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            BingeeDatabase::class.java
        ).build()
        dao = database.notificationDeliveryDao()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun compositeIdentityIsIdempotentAndProviderSubjectDateLeadAware() = runBlocking {
        val base = delivery()
        assertFalse(contains(base))
        dao.insert(base)
        dao.insert(base)
        assertTrue(contains(base))
        assertEquals(1, dao.count())

        listOf(
            base.copy(source = MediaSource.JIKAN),
            base.copy(subjectType = ReleaseSubjectType.SEASON),
            base.copy(eventType = ReleaseEventType.SEASON_PREMIERE),
            base.copy(eventDate = base.eventDate.plusDays(1)),
            base.copy(leadDays = 3)
        ).forEach { dao.insert(it) }
        assertEquals(6, dao.count())
    }

    @Test
    fun pruningRemovesOnlyRowsOlderThanBoundary() = runBlocking {
        val boundary = LocalDate.of(2026, 7, 5)
        dao.insert(delivery(eventDate = boundary.minusDays(1)))
        dao.insert(delivery(eventDate = boundary, leadDays = 3))
        dao.insert(delivery(eventDate = boundary.plusDays(1), leadDays = 7))

        assertEquals(1, dao.prune(boundary))
        assertEquals(2, dao.count())
    }

    private suspend fun contains(value: NotificationDeliveryEntity) = dao.contains(
        value.source,
        value.subjectType,
        value.subjectExternalId,
        value.eventType,
        value.eventDate,
        value.leadDays
    )

    private fun delivery(eventDate: LocalDate = LocalDate.of(2026, 8, 5), leadDays: Int = 1) =
        NotificationDeliveryEntity(
            source = MediaSource.TMDB,
            subjectType = ReleaseSubjectType.MEDIA,
            subjectExternalId = "42",
            eventType = ReleaseEventType.MOVIE_RELEASE,
            eventDate = eventDate,
            leadDays = leadDays,
            notificationId = 7,
            deliveredAt = Instant.parse("2026-08-04T10:00:00Z")
        )
}
