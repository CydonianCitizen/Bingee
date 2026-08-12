package com.cydoniancitizen.bingee.data.notification

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cydoniancitizen.bingee.core.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationDetailIntentTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun movieAndTvTargetsRoundTripWithoutEventPayload() {
        listOf(MediaType.MOVIE, MediaType.SERIES).forEach { mediaType ->
            val target = NotificationNavigationTarget(mediaType, 42)
            val intent = NotificationDetailIntent.intent(context, target)
            assertEquals(target, NotificationDetailIntent.parse(intent))
            assertEquals(2, intent.extras?.size())
            assertFalseExtras(intent.extras?.keySet().orEmpty())
        }
    }

    @Test
    fun malformedIntentIsIgnoredAndPendingIntentIsImmutable() {
        assertNull(NotificationDetailIntent.parse(android.content.Intent()))
        val target = NotificationNavigationTarget(MediaType.MOVIE, 42)
        assertTrue(NotificationDetailIntent.pendingIntent(context, target, 7).isImmutable)
    }

    @Test
    fun unsupportedOrMalformedDetailsIdentityIsRejected() {
        listOf(
            rawIntent("IMDB", "MOVIE", "tt123"),
            rawIntent("UNKNOWN", "MOVIE", "42"),
            rawIntent("TMDB", "UNKNOWN", "42"),
            rawIntent("TMDB", "MOVIE", "abc"),
            rawIntent("TMDB", "MOVIE", "0"),
            rawIntent("TMDB", "MOVIE", "-10"),
            rawIntent("TMDB", "MOVIE", "12.5"),
            rawIntent("TMDB", "MOVIE", " "),
            rawIntent("TMDB", "MOVIE", null)
        ).forEach { assertNull(NotificationDetailIntent.parse(it)) }
    }

    private fun rawIntent(source: String?, mediaType: String?, externalId: String?) =
        android.content.Intent(context, com.cydoniancitizen.bingee.MainActivity::class.java).apply {
            action = NotificationDetailIntent.ACTION_OPEN_DETAILS
            source?.let { putExtra("notification_source", it) }
            mediaType?.let { putExtra("notification_media_type", it) }
            externalId?.let { putExtra("notification_external_id", it) }
        }

    private fun assertFalseExtras(keys: Set<String>) {
        assertTrue(keys.none { it.contains("token", ignoreCase = true) })
        assertTrue(keys.none { it.contains("json", ignoreCase = true) })
        assertTrue(keys.none { it.contains("local", ignoreCase = true) || it.contains("room", ignoreCase = true) })
    }
}
