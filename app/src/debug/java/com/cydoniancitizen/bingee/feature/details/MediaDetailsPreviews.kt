package com.cydoniancitizen.bingee.feature.details

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.debug.FakeMediaData

@Preview(name = "Movie fresh", showBackground = true)
@Preview(name = "Movie dark", showBackground = true, uiMode = 0x20)
@Composable
private fun MoviePreview() = PreviewState(
    MediaDetailsUiState(
        content = DetailContentState.Content(FakeMediaData.freshMovieDetails),
        isInLibrary = false
    )
)

@Preview(name = "TV stale refreshing", showBackground = true)
@Composable
private fun TvRefreshingPreview() = PreviewState(
    MediaDetailsUiState(
        content = DetailContentState.Content(FakeMediaData.staleSeriesDetails),
        refresh = DetailRefreshState.Refreshing,
        isInLibrary = true
    )
)

@Preview(name = "Stale refresh error", showBackground = true)
@Composable
private fun StaleErrorPreview() = PreviewState(
    MediaDetailsUiState(
        content = DetailContentState.Content(FakeMediaData.staleSeriesDetails),
        refresh = DetailRefreshState.Error(AppError.NetworkUnavailable),
        isInLibrary = true
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
        libraryAction = DetailLibraryActionState.UPDATING
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
