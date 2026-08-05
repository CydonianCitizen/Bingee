package com.cydoniancitizen.bingee.feature.details

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import com.cydoniancitizen.bingee.core.model.AnimeWatchProgress
import com.cydoniancitizen.bingee.core.model.CacheFreshness
import com.cydoniancitizen.bingee.core.model.CachedAnimeDetails
import com.cydoniancitizen.bingee.core.model.PersonalRating
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.debug.FakeAnimeData

@Preview(name = "Anime cached detail", showBackground = true)
@Composable
private fun CachedAnimeDetailPreview() = PreviewAnime(
    cached = FakeAnimeData.cachedDetails,
    progress = FakeAnimeData.knownTotalProgress,
    rating = FakeAnimeData.localRating
)

@Preview(name = "Anime stale detail", showBackground = true)
@Composable
private fun StaleAnimeDetailPreview() = PreviewAnime(
    cached = FakeAnimeData.staleDetails,
    progress = FakeAnimeData.unknownTotalProgress,
    refreshError = AppError.NetworkUnavailable
)

@Preview(name = "Anime known total progress", showBackground = true)
@Composable
private fun KnownTotalProgressPreview() = PreviewAnime(
    cached = CachedAnimeDetails(FakeAnimeData.knownTotalDetails, FakeAnimeData.fixedNow, CacheFreshness.FRESH),
    progress = FakeAnimeData.knownTotalProgress
)

@Preview(name = "Anime unknown total progress", showBackground = true)
@Composable
private fun UnknownTotalProgressPreview() = PreviewAnime(
    cached = CachedAnimeDetails(FakeAnimeData.unknownTotalDetails, FakeAnimeData.fixedNow, CacheFreshness.FRESH),
    progress = FakeAnimeData.unknownTotalProgress
)

@Preview(name = "Anime movie progress", showBackground = true)
@Composable
private fun AnimeMovieProgressPreview() = PreviewAnime(
    cached = CachedAnimeDetails(FakeAnimeData.movieAnime, FakeAnimeData.fixedNow, CacheFreshness.FRESH),
    progress = FakeAnimeData.movieProgress,
    rating = PersonalRating(7)
)

@Preview(name = "Anime related entries", showBackground = true)
@Composable
private fun AnimeRelatedEntriesPreview() = PreviewAnime(
    cached = CachedAnimeDetails(FakeAnimeData.relatedAnime, FakeAnimeData.fixedNow, CacheFreshness.FRESH),
    progress = FakeAnimeData.completedProgress,
    rating = FakeAnimeData.localRating
)

@Preview(name = "Anime dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AnimeDarkPreview() = PreviewAnime(
    cached = FakeAnimeData.cachedDetails,
    progress = FakeAnimeData.knownTotalProgress,
    darkTheme = true
)

@Preview(name = "Anime large font", showBackground = true, fontScale = 1.5f)
@Composable
private fun AnimeLargeFontPreview() = PreviewAnime(
    cached = FakeAnimeData.cachedDetails,
    progress = FakeAnimeData.knownTotalProgress,
    rating = FakeAnimeData.localRating
)

@Composable
private fun PreviewAnime(
    cached: CachedAnimeDetails,
    progress: AnimeWatchProgress?,
    rating: PersonalRating? = null,
    refreshError: AppError? = null,
    darkTheme: Boolean = false
) {
    BingeeTheme(darkTheme = darkTheme) {
        AnimeDetailsContent(
            state = AnimeDetailsUiState(
                content = AnimeDetailContentState.Content(cached),
                refreshError = refreshError,
                isInLibrary = true,
                progress = progress,
                rating = DetailRatingState.Ready(rating, selectedValue = rating?.value ?: 5)
            ),
            onBack = {},
            onRefresh = {},
            onRetry = {},
            onToggleLibrary = {},
            onIncrement = {},
            onDecrement = {},
            onSetCount = {},
            onComplete = {},
            onIncomplete = {},
            onOpenRelated = {},
            onSelectRating = {},
            onSaveRating = {},
            onRemoveRating = {},
            onDismissRatingError = {}
        )
    }
}
