package com.cydoniancitizen.bingee.feature.home

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import com.cydoniancitizen.bingee.core.model.CalendarRefreshOutcome
import com.cydoniancitizen.bingee.core.model.CalendarRefreshSummary
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.ReleaseDateCategory
import com.cydoniancitizen.bingee.core.model.ReleaseDateGroup
import com.cydoniancitizen.bingee.core.model.ReleaseEvent
import com.cydoniancitizen.bingee.core.model.ReleaseEventType
import com.cydoniancitizen.bingee.core.model.ReleaseSubjectIdentity
import com.cydoniancitizen.bingee.core.model.ReleaseSubjectType
import java.time.Instant
import java.time.LocalDate

private val previewToday = LocalDate.of(2026, 8, 3)

@Preview(showBackground = true)
@Composable
private fun EmptyHomePreview() = PreviewHome(HomeUiState(content = HomeContentState.Empty, today = previewToday))

@Preview(showBackground = true, fontScale = 1.5f)
@Composable
private fun CalendarHomeLargeTextPreview() = PreviewHome(calendarState())

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CalendarHomeDarkPreview() = PreviewHome(calendarState(), darkTheme = true)

@Preview(showBackground = true)
@Composable
private fun RefreshingHomePreview() = PreviewHome(calendarState().copy(refresh = HomeRefreshState.Refreshing))

@Preview(showBackground = true)
@Composable
private fun PartialHomePreview() = PreviewHome(
    calendarState().copy(
        refresh = HomeRefreshState.Partial(
            CalendarRefreshSummary(
                CalendarRefreshOutcome.PARTIAL_SUCCESS,
                2,
                2,
                1,
                0
            )
        )
    )
)

@Preview(showBackground = true)
@Composable
private fun CredentialHomePreview() = PreviewHome(calendarState().copy(refresh = HomeRefreshState.CredentialRequired))

@Composable
private fun PreviewHome(state: HomeUiState, darkTheme: Boolean = false) {
    BingeeTheme(darkTheme = darkTheme) {
        HomeContent(
            state = state,
            onRefresh = {},
            onRetryLocal = {},
            onDismissFeedback = {},
            onOpenSettings = {},
            onOpenDetails = { _, _ -> }
        )
    }
}

private fun calendarState(): HomeUiState {
    val events = listOf(
        previewEvent(
            id = "movie",
            type = ReleaseEventType.MOVIE_RELEASE,
            subjectType = ReleaseSubjectType.MEDIA,
            mediaType = MediaType.MOVIE,
            title = "A very long movie title without a poster",
            date = previewToday.minusDays(2)
        ),
        previewEvent(
            id = "season-zero",
            type = ReleaseEventType.SEASON_PREMIERE,
            subjectType = ReleaseSubjectType.SEASON,
            mediaType = MediaType.SERIES,
            title = "Series",
            date = previewToday,
            seasonNumber = 0,
            subjectTitle = "Specials"
        ),
        previewEvent(
            id = "episode",
            type = ReleaseEventType.EPISODE_AIRING,
            subjectType = ReleaseSubjectType.EPISODE,
            mediaType = MediaType.SERIES,
            title = "Series",
            date = previewToday.plusDays(4),
            seasonNumber = 2,
            episodeNumber = 3,
            subjectTitle = "The future episode"
        )
    )
    return HomeUiState(
        content = HomeContentState.Events(
            events.groupBy(ReleaseEvent::eventDate).map { (date, rows) ->
                ReleaseDateGroup(
                    date,
                    when {
                        date.isBefore(previewToday) -> ReleaseDateCategory.RECENT
                        date == previewToday -> ReleaseDateCategory.TODAY
                        else -> ReleaseDateCategory.UPCOMING
                    },
                    rows
                )
            }
        ),
        lastSuccessfulRefreshAt = Instant.parse("2026-08-03T10:00:00Z"),
        today = previewToday
    )
}

private fun previewEvent(
    id: String,
    type: ReleaseEventType,
    subjectType: ReleaseSubjectType,
    mediaType: MediaType,
    title: String,
    date: LocalDate,
    seasonNumber: Int? = null,
    episodeNumber: Int? = null,
    subjectTitle: String? = null
) = ReleaseEvent(
    mediaRef = ExternalMediaRef(MediaSource.TMDB, "parent-$id"),
    subject = ReleaseSubjectIdentity(MediaSource.TMDB, subjectType, id, type),
    mediaType = mediaType,
    eventDate = date,
    title = title,
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
    subjectTitle = subjectTitle
)
