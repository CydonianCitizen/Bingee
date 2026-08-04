package com.cydoniancitizen.bingee.data.notification

import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.NotificationDeliveryIdentity
import com.cydoniancitizen.bingee.core.model.ReleaseEventType
import com.cydoniancitizen.bingee.core.model.ReleaseSubjectType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationIdentityTest {
    private val base = NotificationDeliveryIdentity(
        MediaSource.TMDB,
        ReleaseSubjectType.MEDIA,
        "42",
        ReleaseEventType.MOVIE_RELEASE,
        LocalDate.of(2026, 8, 5),
        1
    )

    @Test
    fun idIsDeterministicPositiveAndIndependentOfTitleOrRoomId() {
        val first = deterministicNotificationId(base)
        assertEquals(first, deterministicNotificationId(base.copy()))
        assertTrue(first >= 0)
    }

    @Test
    fun providerSubjectTypeEventTypeDateAndLeadAffectId() {
        val variants = listOf(
            base.copy(source = MediaSource.JIKAN),
            base.copy(subjectType = ReleaseSubjectType.SEASON),
            base.copy(eventType = ReleaseEventType.SEASON_PREMIERE),
            base.copy(eventDate = base.eventDate.plusDays(1)),
            base.copy(leadDays = 3)
        )
        variants.forEach { assertNotEquals(deterministicNotificationId(base), deterministicNotificationId(it)) }
    }
}
