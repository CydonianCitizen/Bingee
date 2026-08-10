package com.cydoniancitizen.bingee.feature.notifications

import androidx.compose.foundation.clickable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.ReleaseEvent
import com.cydoniancitizen.bingee.core.model.ReleaseEventType
import com.cydoniancitizen.bingee.core.model.ReleaseSubjectIdentity
import com.cydoniancitizen.bingee.core.model.ReleaseSubjectType
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NotificationsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.of(2026, 8, 8)

    @Test
    fun noFollowedSeriesEmptyStateIsDisplayed() {
        composeRule.setContent {
            BingeeTheme {
                NotificationsListOrEmptyState(
                    state = NotificationsUiState(
                        contentState = NotificationsContentState.NoFollowedSeries,
                        today = today
                    ),
                    onOpenDetails = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithText("Add TV series to your tracking to see release updates here").assertIsDisplayed()
    }

    @Test
    fun noEventsEmptyStateIsDisplayed() {
        composeRule.setContent {
            BingeeTheme {
                NotificationsListOrEmptyState(
                    state = NotificationsUiState(
                        contentState = NotificationsContentState.NoEvents,
                        today = today
                    ),
                    onOpenDetails = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithText("No new updates from your followed series").assertIsDisplayed()
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
                NotificationsListOrEmptyState(
                    state = NotificationsUiState(
                        contentState = NotificationsContentState.Content(groups),
                        today = today
                    ),
                    onOpenDetails = { ref, mediaType -> opened.set(ref to mediaType) }
                )
            }
        }

        composeRule.onNodeWithText("Upcoming").assertIsDisplayed()
        composeRule.onNodeWithText("Severance").assertIsDisplayed()
        composeRule.onNodeWithText("Today").assertIsDisplayed()
        composeRule.onNodeWithText("Breaking Bad").assertIsDisplayed()

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

@androidx.compose.runtime.Composable
private fun NotificationsListOrEmptyState(
    state: NotificationsUiState,
    onOpenDetails: (ExternalMediaRef, MediaType) -> Unit
) {
    when (val contentState = state.contentState) {
        NotificationsContentState.NoFollowedSeries -> {
            com.cydoniancitizen.bingee.core.designsystem.component.EmptyState(
                title = "Notifications",
                body = androidx.compose.ui.res.stringResource(
                    com.cydoniancitizen.bingee.R.string.notifications_empty_no_followed_series
                )
            )
        }

        NotificationsContentState.NoEvents -> {
            com.cydoniancitizen.bingee.core.designsystem.component.EmptyState(
                title = "Notifications",
                body = androidx.compose.ui.res.stringResource(
                    com.cydoniancitizen.bingee.R.string.notifications_empty_no_events
                )
            )
        }

        is NotificationsContentState.Content -> {
            androidx.compose.foundation.layout.Column {
                contentState.groups.forEach { group ->
                    androidx.compose.material3.Text(
                        text = when (group.category) {
                            NotificationGroupCategory.UPCOMING -> "Upcoming"
                            NotificationGroupCategory.TODAY -> "Today"
                            NotificationGroupCategory.THIS_WEEK -> "This week"
                            NotificationGroupCategory.EARLIER -> "Earlier"
                        }
                    )
                    group.items.forEach { event ->
                        androidx.compose.material3.Text(
                            text = event.title,
                            modifier = androidx.compose.ui.Modifier.clickable {
                                onOpenDetails(event.mediaRef, MediaType.SERIES)
                            }
                        )
                    }
                }
            }
        }

        else -> {}
    }
}
