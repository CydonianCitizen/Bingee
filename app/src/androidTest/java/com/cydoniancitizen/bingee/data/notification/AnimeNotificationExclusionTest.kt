package com.cydoniancitizen.bingee.data.notification

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.NotificationCapabilityStatus
import com.cydoniancitizen.bingee.core.model.ReleaseEvent
import com.cydoniancitizen.bingee.core.model.ReleaseEventType
import com.cydoniancitizen.bingee.core.model.ReleaseNotificationLeadTime
import com.cydoniancitizen.bingee.core.model.ReleaseNotificationPreferences
import com.cydoniancitizen.bingee.core.model.ReleaseSubjectIdentity
import com.cydoniancitizen.bingee.core.model.ReleaseSubjectType
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.library.local.BingeeDatabase
import com.cydoniancitizen.bingee.domain.notification.ReleaseNotificationCapability
import com.cydoniancitizen.bingee.domain.notification.ReleaseNotificationContent
import com.cydoniancitizen.bingee.domain.notification.ReleaseNotifier
import com.cydoniancitizen.bingee.domain.repository.ReleaseCalendarRepository
import com.cydoniancitizen.bingee.domain.repository.ReleaseNotificationPreferencesRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnimeNotificationExclusionTest {
    private lateinit var database: BingeeDatabase
    private val today = LocalDate.of(2026, 8, 5)
    private val clock = Clock.fixed(Instant.parse("2026-08-05T10:00:00Z"), ZoneOffset.UTC)

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BingeeDatabase::class.java).build()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun animePremiereHasNoCategoryMappingPostOrRoomDeliveryLedgerRow() = runBlocking {
        val event = ReleaseEvent(
            mediaRef = ExternalMediaRef(MediaSource.JIKAN, "52991"),
            subject = ReleaseSubjectIdentity(
                source = MediaSource.JIKAN,
                subjectType = ReleaseSubjectType.MEDIA,
                externalId = "52991",
                eventType = ReleaseEventType.ANIME_PREMIERE
            ),
            mediaType = MediaType.ANIME,
            eventDate = today,
            title = "Anime premiere"
        )
        val delivery = RoomNotificationDeliveryRepository(database.notificationDeliveryDao())
        val notifier = RecordingNotifier()
        val mapper = AndroidReleaseNotificationContentMapper(
            ApplicationProvider.getApplicationContext()
        )
        val summary = DefaultNotificationDispatchCoordinator(
            preferencesRepository = FakePreferences(),
            capability = AvailableCapability(),
            calendarRepository = OneEventCalendar(event),
            deliveryRepository = delivery,
            contentMapper = mapper,
            notifier = notifier,
            clock = clock
        ).dispatch()

        assertFalse(ReleaseNotificationPreferences().includes(ReleaseEventType.ANIME_PREMIERE))
        assertEquals(1, summary.candidates)
        assertEquals(1, summary.skippedByCategory)
        assertEquals(0, summary.posted)
        assertTrue(notifier.events.isEmpty())
        assertEquals(0, database.notificationDeliveryDao().count())

        try {
            mapper.map(event, 0)
            throw AssertionError("Anime content must not be mapped")
        } catch (_: IllegalStateException) {
            // The mapper has no movie/season fallback.
        }
    }

    private class FakePreferences : ReleaseNotificationPreferencesRepository {
        override val preferences = MutableStateFlow(
            ReleaseNotificationPreferences(enabled = true, leadTime = ReleaseNotificationLeadTime.SAME_DAY)
        )

        override suspend fun setEnabled(enabled: Boolean) = Unit
        override suspend fun setLeadTime(leadTime: ReleaseNotificationLeadTime) = Unit
        override suspend fun setMovieReleases(enabled: Boolean) = Unit
        override suspend fun setSeasonPremieres(enabled: Boolean) = Unit
        override suspend fun setEpisodeAirings(enabled: Boolean) = Unit
    }

    private class AvailableCapability : ReleaseNotificationCapability {
        override fun ensureChannel() = Unit
        override fun status() = NotificationCapabilityStatus.AVAILABLE
        override fun openSystemSettings() = Unit
    }

    private class OneEventCalendar(private val event: ReleaseEvent) : ReleaseCalendarRepository {
        override fun observeEvents(fromDate: LocalDate): Flow<AppResult<List<ReleaseEvent>>> =
            flowOf(AppResult.Success(listOf(event)))

        override fun observeLastSuccessfulRefresh(): Flow<AppResult<Instant?>> = flowOf(AppResult.Success(null))

        override suspend fun getEvents(
            fromDate: LocalDate,
            throughDate: LocalDate,
            limit: Int
        ): AppResult<List<ReleaseEvent>> = AppResult.Success(listOf(event))

        override suspend fun backfill(): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun markRefreshSuccessful(at: Instant): AppResult<Unit> = AppResult.Success(Unit)
    }

    private class RecordingNotifier : ReleaseNotifier {
        val events = mutableListOf<ReleaseEvent>()

        override fun post(
            event: ReleaseEvent,
            notificationId: Int,
            content: ReleaseNotificationContent
        ): AppResult<Unit> {
            events += event
            return AppResult.Success(Unit)
        }
    }
}
