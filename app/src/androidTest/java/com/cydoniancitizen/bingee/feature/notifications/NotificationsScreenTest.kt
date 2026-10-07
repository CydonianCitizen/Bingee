package com.cydoniancitizen.bingee.feature.notifications

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.ReleaseEvent
import com.cydoniancitizen.bingee.core.model.ReleaseEventType
import com.cydoniancitizen.bingee.core.model.ReleaseSubjectIdentity
import com.cydoniancitizen.bingee.core.model.ReleaseSubjectType
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.testutil.TestLocaleRule
import com.cydoniancitizen.bingee.testutil.localizedTestContext
import java.io.File
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NotificationsScreenTest {
    @get:Rule(order = 0)
    val localeRule = TestLocaleRule()

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    private val today = LocalDate.of(2026, 8, 8)
    private val context = localizedTestContext

    @Test
    fun loadingErrorRetryAndBackUseProductionControls() {
        var state by mutableStateOf(NotificationsUiState(today = today))
        val refreshed = AtomicInteger()
        val backed = AtomicInteger()
        composeRule.setContent {
            BingeeTheme {
                NotificationsContent(
                    state = state,
                    onBack = { backed.incrementAndGet() },
                    onRefresh = {
                        refreshed.incrementAndGet()
                        state = state.copy(refreshState = NotificationRefreshState.Refreshing)
                    },
                    onOpenDetails = { _, _ -> }
                )
            }
        }
        composeRule.onNodeWithText(context.getString(R.string.notifications_loading)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.detail_back)).performClick()
        assertEquals(1, backed.get())
        composeRule.runOnIdle {
            state = state.copy(contentState = NotificationsContentState.Error(AppError.NetworkUnavailable))
        }
        composeRule.onNodeWithText(context.getString(R.string.notifications_refresh_failed)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.action_retry)).performClick().assertIsNotEnabled()
        assertEquals(1, refreshed.get())
    }

    @Test
    fun noFollowedSeriesEmptyStateIsDisplayed() {
        composeRule.setContent {
            BingeeTheme {
                NotificationsContent(
                    state = NotificationsUiState(
                        contentState = NotificationsContentState.NoFollowedSeries,
                        today = today
                    ),
                    onBack = {},
                    onRefresh = {},
                    onOpenDetails = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithText(
            context.getString(R.string.notifications_empty_no_followed_series)
        ).assertIsDisplayed()
    }

    @Test
    fun noEventsEmptyStateIsDisplayed() {
        composeRule.setContent {
            BingeeTheme {
                NotificationsContent(
                    state = NotificationsUiState(
                        contentState = NotificationsContentState.NoEvents,
                        today = today
                    ),
                    onBack = {},
                    onRefresh = {},
                    onOpenDetails = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.notifications_empty_no_events)).assertIsDisplayed()
    }

    @Test
    fun groupedEventsRenderAndClickNavigatesToDetails() {
        val opened = AtomicReference<Pair<ExternalMediaRef, MediaType>>()
        val todayEvent = createEvent("101", "Breaking Bad", today)
        val upcomingEvent = createEvent("102", "Severance", today.plusDays(3))

        val groups = listOf(
            NotificationGroup(NotificationGroupCategory.UPCOMING, listOf(upcomingEvent)),
            NotificationGroup(NotificationGroupCategory.TODAY, listOf(todayEvent))
        )

        composeRule.setContent {
            BingeeTheme {
                NotificationsContent(
                    state = NotificationsUiState(
                        contentState = NotificationsContentState.Content(groups),
                        today = today
                    ),
                    onBack = {},
                    onRefresh = {},
                    onOpenDetails = { ref, mediaType -> opened.set(ref to mediaType) }
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.notifications_group_upcoming)).assertIsDisplayed()
        composeRule.onNodeWithText("Severance").assertIsDisplayed()
        composeRule.onNode(
            hasText(context.getString(R.string.notifications_group_today)) and isHeading()
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Breaking Bad").assertIsDisplayed()

        // Exercise the production poster: neither its placeholder nor artwork repeats the card title.
        composeRule.onAllNodesWithContentDescription(context.getString(R.string.poster_missing, "Breaking Bad"))
            .assertCountEquals(0)
        if (InstrumentationRegistry.getArguments().getString("captureUi") == "true") {
            val file =
                File(context.cacheDir, "notifications-${context.resources.configuration.locales[0].language}.png")
            file.outputStream().use {
                composeRule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }

        composeRule.onNodeWithText("Breaking Bad").performClick()
        assertEquals(todayEvent.mediaRef to MediaType.SERIES, opened.get())
    }

    private fun createEvent(id: String, title: String, date: LocalDate) = ReleaseEvent(
        mediaRef = ExternalMediaRef(MediaSource.TMDB, id),
        subject = ReleaseSubjectIdentity(
            MediaSource.TMDB,
            ReleaseSubjectType.EPISODE,
            "ep-$id",
            ReleaseEventType.EPISODE_AIRING
        ),
        mediaType = MediaType.SERIES,
        eventDate = date,
        title = title,
        seasonNumber = 1,
        episodeNumber = 1,
        subjectTitle = "Episode title $id"
    )
}
