package com.cydoniancitizen.bingee.data.notification

import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.NotificationCapabilityStatus
import com.cydoniancitizen.bingee.core.model.NotificationDelivery
import com.cydoniancitizen.bingee.core.model.NotificationDeliveryIdentity
import com.cydoniancitizen.bingee.core.model.ReleaseEvent
import com.cydoniancitizen.bingee.core.model.ReleaseEventType
import com.cydoniancitizen.bingee.core.model.ReleaseNotificationLeadTime
import com.cydoniancitizen.bingee.core.model.ReleaseNotificationPreferences
import com.cydoniancitizen.bingee.core.model.ReleaseSubjectIdentity
import com.cydoniancitizen.bingee.core.model.ReleaseSubjectType
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.domain.notification.ReleaseNotificationCapability
import com.cydoniancitizen.bingee.domain.notification.ReleaseNotificationContent
import com.cydoniancitizen.bingee.domain.notification.ReleaseNotificationContentMapper
import com.cydoniancitizen.bingee.domain.notification.ReleaseNotifier
import com.cydoniancitizen.bingee.domain.repository.NotificationClaim
import com.cydoniancitizen.bingee.domain.repository.NotificationDeliveryRepository
import com.cydoniancitizen.bingee.domain.repository.ReleaseCalendarRepository
import com.cydoniancitizen.bingee.domain.repository.ReleaseNotificationPreferencesRepository
import com.cydoniancitizen.bingee.testutil.TestCalendarDateSource
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultNotificationDispatchCoordinatorTest {
    private val today = LocalDate.of(2026, 8, 4)
    private val clock = Clock.fixed(Instant.parse("2026-08-04T10:00:00Z"), ZoneOffset.UTC)

    @Test
    fun disabledOrUnavailableStopsWithoutReadingEvents() = runTest {
        val calendar = FakeCalendar()
        val disabled = coordinator(
            FakePreferences(ReleaseNotificationPreferences(enabled = false)),
            FakeCapability(NotificationCapabilityStatus.AVAILABLE),
            calendar
        ).dispatch()
        assertEquals(0, disabled.candidates)
        assertEquals(0, calendar.calls)

        val blocked = coordinator(
            FakePreferences(ReleaseNotificationPreferences(enabled = true)),
            FakeCapability(NotificationCapabilityStatus.SYSTEM_BLOCKED),
            calendar
        ).dispatch()
        assertEquals(NotificationCapabilityStatus.SYSTEM_BLOCKED, blocked.capability)
        assertEquals(0, calendar.calls)
    }

    @Test
    fun dueEventsFilterCategoriesAndCompleteAfterPost() = runTest {
        val movie = event("movie", ReleaseEventType.MOVIE_RELEASE, today.plusDays(1))
        val season = event("season", ReleaseEventType.SEASON_PREMIERE, today.plusDays(1))
        val deliveredEpisode = event("episode", ReleaseEventType.EPISODE_AIRING, today)
        val calendar = FakeCalendar(listOf(movie, season, deliveredEpisode))
        val delivery = FakeDeliveryRepository().apply {
            rows += deliveredEpisode.identity(ReleaseNotificationLeadTime.ONE_DAY.days)
        }
        val notifier = FakeNotifier()
        val summary = coordinator(
            FakePreferences(
                ReleaseNotificationPreferences(enabled = true, seasonPremieres = false)
            ),
            FakeCapability(NotificationCapabilityStatus.AVAILABLE),
            calendar,
            delivery,
            notifier
        ).dispatch()

        assertEquals(3, summary.candidates)
        assertEquals(1, summary.posted)
        assertEquals(1, summary.skippedByCategory)
        assertEquals(1, summary.alreadyDelivered)
        assertEquals(listOf("movie"), notifier.postedSubjects)
        assertTrue(delivery.rows.contains(movie.identity(1)))
        assertEquals(1, delivery.batchLookupCalls)
        assertEquals(today.minusDays(30), delivery.prunedBefore)
        assertFalse(summary.transientFailure)
    }

    @Test
    fun eligibleEventsAfterIrrelevantRowsAreNotLostToScanSafetyCap() = runTest {
        val later = event("later", ReleaseEventType.MOVIE_RELEASE, today)
        val calendar = FakeCalendar(
            List(200) { event("season-$it", ReleaseEventType.SEASON_PREMIERE, today) } + later
        )
        val notifier = FakeNotifier()

        val summary = coordinator(
            FakePreferences(
                ReleaseNotificationPreferences(enabled = true, seasonPremieres = false)
            ),
            FakeCapability(NotificationCapabilityStatus.AVAILABLE),
            calendar,
            notifier = notifier
        ).dispatch()

        assertEquals(1, summary.posted)
        assertEquals(listOf("later"), notifier.postedSubjects)
    }

    @Test
    fun dispatchCapAppliesAfterDeliveryFiltering() = runTest {
        val events = (0..DefaultNotificationDispatchCoordinator.MAX_CANDIDATES).map {
            event("movie-$it", ReleaseEventType.MOVIE_RELEASE, today)
        }
        val delivery = FakeDeliveryRepository().apply {
            rows += events.first().identity(0)
        }
        val notifier = FakeNotifier()

        val summary = coordinator(
            FakePreferences(
                ReleaseNotificationPreferences(
                    enabled = true,
                    leadTime = ReleaseNotificationLeadTime.SAME_DAY
                )
            ),
            FakeCapability(NotificationCapabilityStatus.AVAILABLE),
            FakeCalendar(events),
            delivery,
            notifier
        ).dispatch()

        assertEquals(DefaultNotificationDispatchCoordinator.MAX_CANDIDATES, summary.posted)
        assertEquals(1, summary.alreadyDelivered)
        assertFalse(notifier.postedSubjects.contains("movie-0"))
    }

    @Test
    fun postAndPersistenceFailuresRemainIndependentAndRetryable() = runTest {
        val failedPost = event("post-fail", ReleaseEventType.MOVIE_RELEASE, today)
        val failedRecord = event("record-fail", ReleaseEventType.MOVIE_RELEASE, today)
        val successful = event("ok", ReleaseEventType.MOVIE_RELEASE, today)
        val notifier = FakeNotifier(failSubjects = setOf("post-fail"))
        val delivery = FakeDeliveryRepository(failRecordSubjects = setOf("record-fail"))
        val summary = coordinator(
            FakePreferences(
                ReleaseNotificationPreferences(
                    enabled = true,
                    leadTime = ReleaseNotificationLeadTime.SAME_DAY
                )
            ),
            FakeCapability(NotificationCapabilityStatus.AVAILABLE),
            FakeCalendar(listOf(failedPost, failedRecord, successful)),
            delivery,
            notifier
        ).dispatch()

        assertEquals(2, summary.posted)
        assertEquals(2, summary.failed)
        assertTrue(summary.transientFailure)
        assertFalse(delivery.rows.any { it.subjectExternalId == "post-fail" })
        assertFalse(delivery.rows.any { it.subjectExternalId == "record-fail" })
        assertTrue(delivery.rows.any { it.subjectExternalId == "ok" })
        assertEquals(listOf("post-fail", "record-fail", "ok"), notifier.postedSubjects)

        // A retry after a successful post must not immediately publish the same event again.
        coordinator(
            FakePreferences(
                ReleaseNotificationPreferences(enabled = true, leadTime = ReleaseNotificationLeadTime.SAME_DAY)
            ),
            FakeCapability(NotificationCapabilityStatus.AVAILABLE),
            FakeCalendar(listOf(failedRecord, successful)),
            delivery,
            notifier
        ).dispatch()
        assertEquals(1, notifier.postedSubjects.count { it == "record-fail" })
        assertEquals(1, notifier.postedSubjects.count { it == "ok" })
    }

    @Test
    fun expiredClaimCanBeReclaimedAfterProcessRestart() = runTest {
        val event = event("restart", ReleaseEventType.MOVIE_RELEASE, today)
        val delivery = FakeDeliveryRepository(failRecordSubjects = setOf("restart"))
        val notifier = FakeNotifier()
        val preferences = FakePreferences(
            ReleaseNotificationPreferences(enabled = true, leadTime = ReleaseNotificationLeadTime.SAME_DAY)
        )
        val calendar = FakeCalendar(listOf(event))
        coordinator(preferences, FakeCapability(NotificationCapabilityStatus.AVAILABLE), calendar, delivery, notifier)
            .dispatch()
        coordinator(preferences, FakeCapability(NotificationCapabilityStatus.AVAILABLE), calendar, delivery, notifier)
            .dispatch()
        assertEquals(1, notifier.postedSubjects.size)

        val later = Clock.offset(clock, java.time.Duration.ofMinutes(6))
        coordinator(
            preferences,
            FakeCapability(NotificationCapabilityStatus.AVAILABLE),
            calendar,
            delivery,
            notifier,
            dispatchClock = later
        ).dispatch()
        assertEquals(2, notifier.postedSubjects.size)
    }

    @Test
    fun anExistingWorkerClaimPreventsAnotherWorkerFromPosting() = runTest {
        val event = event("parallel", ReleaseEventType.MOVIE_RELEASE, today)
        val delivery = FakeDeliveryRepository()
        val identity = event.identity(0)
        assertTrue(delivery.claim(identity, 7, clock.instant()) is AppResult.Success)
        val notifier = FakeNotifier()
        val summary = coordinator(
            FakePreferences(
                ReleaseNotificationPreferences(enabled = true, leadTime = ReleaseNotificationLeadTime.SAME_DAY)
            ),
            FakeCapability(NotificationCapabilityStatus.AVAILABLE),
            FakeCalendar(listOf(event)),
            delivery,
            notifier
        ).dispatch()
        assertTrue(summary.transientFailure)
        assertTrue(notifier.postedSubjects.isEmpty())
    }

    @Test
    fun failedPostReleasesClaimForImmediateRetry() = runTest {
        val event = event("retry", ReleaseEventType.MOVIE_RELEASE, today)
        val preferences = FakePreferences(
            ReleaseNotificationPreferences(enabled = true, leadTime = ReleaseNotificationLeadTime.SAME_DAY)
        )
        val capability = FakeCapability(NotificationCapabilityStatus.AVAILABLE)
        val calendar = FakeCalendar(listOf(event))
        val delivery = FakeDeliveryRepository()
        coordinator(preferences, capability, calendar, delivery, FakeNotifier(setOf("retry"))).dispatch()

        val notifier = FakeNotifier()
        val retry = coordinator(preferences, capability, calendar, delivery, notifier).dispatch()
        assertEquals(1, retry.posted)
        assertEquals(listOf("retry"), notifier.postedSubjects)
        assertTrue(event.identity(0) in delivery.rows)
    }

    private fun coordinator(
        preferences: FakePreferences,
        capability: FakeCapability,
        calendar: FakeCalendar,
        delivery: FakeDeliveryRepository = FakeDeliveryRepository(),
        notifier: FakeNotifier = FakeNotifier(),
        contentMapper: ReleaseNotificationContentMapper = FakeContentMapper(),
        dispatchClock: Clock = clock
    ) = DefaultNotificationDispatchCoordinator(
        preferences,
        capability,
        calendar,
        delivery,
        contentMapper,
        notifier,
        dispatchClock,
        TestCalendarDateSource(today)
    )

    private fun event(subjectId: String, type: ReleaseEventType, date: LocalDate): ReleaseEvent {
        val subjectType = when (type) {
            ReleaseEventType.MOVIE_RELEASE -> ReleaseSubjectType.MEDIA
            ReleaseEventType.SEASON_PREMIERE -> ReleaseSubjectType.SEASON
            ReleaseEventType.EPISODE_AIRING -> ReleaseSubjectType.EPISODE
        }
        val mediaType = when (type) {
            ReleaseEventType.MOVIE_RELEASE -> MediaType.MOVIE
            else -> MediaType.SERIES
        }
        val source = MediaSource.TMDB
        return ReleaseEvent(
            mediaRef = ExternalMediaRef(source, "900"),
            subject = ReleaseSubjectIdentity(source, subjectType, subjectId, type),
            mediaType = mediaType,
            eventDate = date,
            title = "Fixture",
            seasonNumber = if (mediaType == MediaType.SERIES) 1 else null,
            episodeNumber = if (type == ReleaseEventType.EPISODE_AIRING) 2 else null
        )
    }

    private fun ReleaseEvent.identity(leadDays: Int) = NotificationDeliveryIdentity(
        subject.source,
        subject.subjectType,
        subject.externalId,
        subject.eventType,
        eventDate,
        leadDays
    )

    private class FakePreferences(initial: ReleaseNotificationPreferences) :
        ReleaseNotificationPreferencesRepository {
        override val preferences = MutableStateFlow(initial)
        override suspend fun setEnabled(enabled: Boolean) = Unit
        override suspend fun setLeadTime(leadTime: ReleaseNotificationLeadTime) = Unit
        override suspend fun setMovieReleases(enabled: Boolean) = Unit
        override suspend fun setSeasonPremieres(enabled: Boolean) = Unit
        override suspend fun setEpisodeAirings(enabled: Boolean) = Unit
    }

    private class FakeCapability(var value: NotificationCapabilityStatus) : ReleaseNotificationCapability {
        override fun ensureChannel() = Unit
        override fun status(): NotificationCapabilityStatus = value
        override fun openSystemSettings() = Unit
    }

    private class FakeCalendar(private val events: List<ReleaseEvent> = emptyList()) : ReleaseCalendarRepository {
        var calls = 0
        override fun observeEvents(fromDate: LocalDate): Flow<AppResult<List<ReleaseEvent>>> =
            flowOf(AppResult.Success(events))
        override fun observeLastSuccessfulRefresh(): Flow<AppResult<Instant?>> = flowOf(AppResult.Success(null))
        override suspend fun getEvents(fromDate: LocalDate, throughDate: LocalDate): AppResult<List<ReleaseEvent>> {
            calls++
            return AppResult.Success(events)
        }
        override suspend fun backfill(): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun markRefreshSuccessful(at: Instant): AppResult<Unit> = AppResult.Success(Unit)
    }

    private class FakeDeliveryRepository(private val failRecordSubjects: Set<String> = emptySet()) :
        NotificationDeliveryRepository {
        val rows = linkedSetOf<NotificationDeliveryIdentity>()
        private val claims = mutableMapOf<NotificationDeliveryIdentity, Pair<String, Instant>>()
        var prunedBefore: LocalDate? = null
        var batchLookupCalls = 0
        override suspend fun findDelivered(
            identities: Set<NotificationDeliveryIdentity>
        ): AppResult<Set<NotificationDeliveryIdentity>> {
            batchLookupCalls++
            return AppResult.Success(rows.intersect(identities))
        }
        override suspend fun claim(
            identity: NotificationDeliveryIdentity,
            notificationId: Int,
            now: Instant
        ): AppResult<NotificationClaim> = synchronized(this) {
            if (identity in rows) return@synchronized AppResult.Success(NotificationClaim.AlreadyDelivered)
            val existing = claims[identity]
            if (existing != null && now.isBefore(existing.second)) {
                return@synchronized AppResult.Success(NotificationClaim.InFlight)
            }
            val token = UUID.randomUUID().toString()
            claims[identity] = token to now.plusSeconds(300)
            AppResult.Success(NotificationClaim.Acquired(token))
        }

        override suspend fun complete(delivery: NotificationDelivery, token: String): AppResult<Boolean> {
            if (delivery.identity.subjectExternalId in failRecordSubjects) {
                return AppResult.Failure(AppError.LocalStorageFailure)
            }
            if (claims[delivery.identity]?.first != token) return AppResult.Success(false)
            claims.remove(delivery.identity)
            rows += delivery.identity
            return AppResult.Success(true)
        }
        override suspend fun release(identity: NotificationDeliveryIdentity, token: String): AppResult<Unit> {
            if (claims[identity]?.first == token) claims.remove(identity)
            return AppResult.Success(Unit)
        }
        override suspend fun prune(eventDateBefore: LocalDate): AppResult<Int> {
            prunedBefore = eventDateBefore
            return AppResult.Success(0)
        }
    }

    private class FakeContentMapper : ReleaseNotificationContentMapper {
        override fun map(event: ReleaseEvent, daysUntilEvent: Int) = ReleaseNotificationContent("Fixture", "Fixture")
    }

    private class FakeNotifier(private val failSubjects: Set<String> = emptySet()) : ReleaseNotifier {
        val postedSubjects = mutableListOf<String>()
        override fun post(
            event: ReleaseEvent,
            notificationId: Int,
            content: ReleaseNotificationContent
        ): AppResult<Unit> {
            postedSubjects += event.subject.externalId
            return if (event.subject.externalId in failSubjects) {
                AppResult.Failure(AppError.NotificationDeliveryFailure)
            } else {
                AppResult.Success(Unit)
            }
        }
    }
}
