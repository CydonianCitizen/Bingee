package com.cydoniancitizen.bingee.data.notification

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.NotificationDelivery
import com.cydoniancitizen.bingee.core.model.NotificationDeliveryIdentity
import com.cydoniancitizen.bingee.core.model.ReleaseEventType
import com.cydoniancitizen.bingee.core.model.ReleaseSubjectType
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.library.local.BingeeDatabase
import com.cydoniancitizen.bingee.domain.repository.NotificationClaim
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomNotificationDeliveryRepositoryTest {
    @Test
    fun pendingClaimSurvivesDatabaseReopenAndExpires() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "notification-claim-${UUID.randomUUID()}.db"
        val identity = NotificationDeliveryIdentity(
            MediaSource.TMDB,
            ReleaseSubjectType.MEDIA,
            "42",
            ReleaseEventType.MOVIE_RELEASE,
            LocalDate.of(2026, 8, 5),
            1
        )
        val now = Instant.parse("2026-08-04T10:00:00Z")
        var database: BingeeDatabase? = null
        try {
            val firstDatabase = Room.databaseBuilder(context, BingeeDatabase::class.java, name).build()
            database = firstDatabase
            val firstRepository = RoomNotificationDeliveryRepository(firstDatabase.notificationDeliveryDao())
            val first = (firstRepository.claim(identity, 42, now) as AppResult.Success).value
            assertTrue(first is NotificationClaim.Acquired)
            firstDatabase.close()

            val reopenedDatabase = Room.databaseBuilder(context, BingeeDatabase::class.java, name).build()
            database = reopenedDatabase
            val reopened = RoomNotificationDeliveryRepository(reopenedDatabase.notificationDeliveryDao())
            assertEquals(
                NotificationClaim.InFlight,
                (reopened.claim(identity, 42, now.plusSeconds(60)) as AppResult.Success).value
            )
            val recovered = (reopened.claim(identity, 42, now.plusSeconds(6 * 60)) as AppResult.Success).value
            assertTrue(recovered is NotificationClaim.Acquired)
            assertNotEquals(
                (first as NotificationClaim.Acquired).token,
                (recovered as NotificationClaim.Acquired).token
            )
            assertEquals(
                AppResult.Success(false),
                reopened.complete(NotificationDelivery(identity, 42, now), first.token)
            )
            assertEquals(
                AppResult.Success(true),
                reopened.complete(NotificationDelivery(identity, 42, now.plusSeconds(6 * 60)), recovered.token)
            )
            assertEquals(
                AppResult.Success(setOf(identity)),
                reopened.findDelivered(setOf(identity))
            )
        } finally {
            database?.close()
            context.deleteDatabase(name)
        }
    }
}
