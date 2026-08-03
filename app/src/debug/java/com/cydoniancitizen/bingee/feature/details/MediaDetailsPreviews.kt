package com.cydoniancitizen.bingee.feature.details

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import com.cydoniancitizen.bingee.core.model.MovieWatchState
import com.cydoniancitizen.bingee.core.model.PersonalRating
import com.cydoniancitizen.bingee.core.model.deriveSeriesProgress
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.debug.FakeMediaData

@Preview(name = "Movie fresh", showBackground = true)
@Preview(name = "Movie dark", showBackground = true, uiMode = 0x20)
@Composable
private fun MoviePreview() = PreviewState(
    MediaDetailsUiState(
        content = DetailContentState.Content(FakeMediaData.freshMovieDetails),
        isInLibrary = false,
        rating = DetailRatingState.Ready(PersonalRating(10), selectedValue = 10),
        movieProgress = MovieProgressState.Ready(MovieWatchState.Watched(FakeMediaData.fixedNow))
    )
)

@Preview(name = "TV stale refreshing", showBackground = true)
@Composable
private fun TvRefreshingPreview() = PreviewState(
    MediaDetailsUiState(
        content = DetailContentState.Content(FakeMediaData.staleSeriesDetails),
        refresh = DetailRefreshState.Refreshing,
        isInLibrary = true,
        series = SeriesDetailUiState(
            content = SeriesContentState.Ready(
                FakeMediaData.previewSeasons,
                deriveSeriesProgress(FakeMediaData.previewSeasons)
            ),
            expandedSeasons = FakeMediaData.previewSeasons.map { it.season.externalRef }.toSet(),
            pendingEpisodes = setOf(
                FakeMediaData.previewSeasons[1].episodes.first().episode.externalRef
            )
        )
    )
)

@Preview(name = "Stale refresh error", showBackground = true)
@Composable
private fun StaleErrorPreview() = PreviewState(
    MediaDetailsUiState(
        content = DetailContentState.Content(FakeMediaData.staleSeriesDetails),
        refresh = DetailRefreshState.Error(AppError.NetworkUnavailable),
        isInLibrary = true,
        series = SeriesDetailUiState(
            content = SeriesContentState.Ready(
                FakeMediaData.previewSeasons,
                deriveSeriesProgress(FakeMediaData.previewSeasons)
            ),
            expandedSeasons = setOf(FakeMediaData.previewSeasons[1].season.externalRef),
            seasonLoads = mapOf(
                FakeMediaData.previewSeasons[1].season.externalRef to
                    SeasonLoadState.Error(AppError.NetworkUnavailable)
            )
        )
    )
)

@Preview(name = "Loading", showBackground = true)
@Composable
private fun LoadingPreview() = PreviewState(MediaDetailsUiState(content = DetailContentState.Loading))

@Preview(name = "No cache error", showBackground = true)
@Composable
private fun ErrorPreview() = PreviewState(
    MediaDetailsUiState(content = DetailContentState.Error(AppError.NetworkUnavailable))
)

@Preview(name = "Unauthorized", showBackground = true)
@Composable
private fun UnauthorizedPreview() = PreviewState(
    MediaDetailsUiState(content = DetailContentState.Error(AppError.Unauthorized))
)

@Preview(name = "Library updating", showBackground = true, fontScale = 1.5f)
@Composable
private fun UpdatingLargeFontPreview() = PreviewState(
    MediaDetailsUiState(
        content = DetailContentState.Content(FakeMediaData.freshMovieDetails),
        isInLibrary = false,
        libraryAction = DetailLibraryActionState.UPDATING,
        rating = DetailRatingState.Ready(
            rating = PersonalRating(1),
            selectedValue = 1,
            updating = true,
            error = AppError.LocalStorageFailure
        ),
        movieProgress = MovieProgressState.Ready(MovieWatchState.Unwatched, updating = true)
    )
)

@Composable
private fun PreviewState(state: MediaDetailsUiState) {
    BingeeTheme {
        MediaDetailsContent(
            state = state,
            onBack = {},
            onRefresh = {},
            onRetry = {},
            onToggleLibrary = {},
            onDismissLibraryError = {},
            onOpenSettings = {}
        )
    }
}
