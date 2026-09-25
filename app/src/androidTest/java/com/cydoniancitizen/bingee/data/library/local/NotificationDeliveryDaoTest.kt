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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
        assertTrue(dao.findBetween(base.eventDate, base.eventDate, base.leadDays).isEmpty())
        dao.insert(base)
        dao.insert(base)
        assertEquals(listOf(base), dao.findBetween(base.eventDate, base.eventDate, base.leadDays))
        assertEquals(1, dao.count())

        listOf(
            base.copy(source = MediaSource.IMDB),
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

    @Test
    fun twoWorkersCannotTakeTheSameClaimAndExpiredClaimIsRecoverable() = runBlocking {
        val pending = delivery().copy(
            deliveredAt = Instant.EPOCH,
            claimToken = "first",
            claimExpiresAtMs = 1_000L
        )
        assertTrue(dao.insert(pending) >= 0)
        assertEquals(-1L, dao.insert(pending.copy(claimToken = "second")))
        assertTrue(dao.findBetween(pending.eventDate, pending.eventDate, pending.leadDays).isEmpty())
        val contenders = listOf("second", "third").map { token ->
            async(Dispatchers.IO) {
                dao.takeExpiredClaim(
                    pending.source, pending.subjectType, pending.subjectExternalId,
                    pending.eventType, pending.eventDate, pending.leadDays,
                    token, 2_000L, 1_000L
                )
            }
        }
        assertEquals(1, contenders.sumOf { it.await() })
        val winner = dao.find(
            pending.source,
            pending.subjectType,
            pending.subjectExternalId,
            pending.eventType,
            pending.eventDate,
            pending.leadDays
        )?.claimToken!!
        val completedAt = Instant.parse("2026-08-04T10:00:00Z")
        assertEquals(
            0,
            dao.completeClaim(
                pending.source, pending.subjectType, pending.subjectExternalId,
                pending.eventType, pending.eventDate, pending.leadDays,
                "first", pending.notificationId, completedAt
            )
        )
        assertEquals(
            1,
            dao.completeClaim(
                pending.source, pending.subjectType, pending.subjectExternalId,
                pending.eventType, pending.eventDate, pending.leadDays,
                winner, pending.notificationId, completedAt
            )
        )
        assertEquals(
            listOf(pending.copy(deliveredAt = completedAt, claimToken = null, claimExpiresAtMs = null)),
            dao.findBetween(pending.eventDate, pending.eventDate, pending.leadDays)
        )
    }

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
