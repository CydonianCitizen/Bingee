package com.cydoniancitizen.bingee.data.notification

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSource
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
            val target = NotificationNavigationTarget(ExternalMediaRef(MediaSource.TMDB, "42"), mediaType)
            val intent = NotificationDetailIntent.intent(context, target)
            assertEquals(target, NotificationDetailIntent.parse(intent))
            assertEquals(3, intent.extras?.size())
            assertFalseExtras(intent.extras?.keySet().orEmpty())
        }
    }

    @Test
    fun malformedIntentIsIgnoredAndPendingIntentIsImmutable() {
        assertNull(NotificationDetailIntent.parse(android.content.Intent()))
        val target = NotificationNavigationTarget(ExternalMediaRef(MediaSource.TMDB, "42"), MediaType.MOVIE)
        assertTrue(NotificationDetailIntent.pendingIntent(context, target, 7).isImmutable)
    }

    private fun assertFalseExtras(keys: Set<String>) {
        assertTrue(keys.none { it.contains("token", ignoreCase = true) })
        assertTrue(keys.none { it.contains("json", ignoreCase = true) })
        assertTrue(keys.none { it.contains("local", ignoreCase = true) || it.contains("room", ignoreCase = true) })
    }
}
