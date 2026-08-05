package com.cydoniancitizen.bingee.feature.search

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import com.cydoniancitizen.bingee.core.model.MediaSearchCategory
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.debug.FakeAnimeData

@Preview(name = "Anime result", showBackground = true)
@Composable
private fun AnimeResultPreview() {
    AnimeSearchPreview(
        SearchUiState(
            query = "synthetic anime",
            category = MediaSearchCategory.ANIME,
            credentialAvailability = SearchCredentialAvailability.REQUIRED,
            content = SearchContentState.Results(
                items = FakeAnimeData.searchResults,
                currentPage = 1,
                totalPages = 1,
                nextPage = NextPageState.End
            )
        )
    )
}

@Preview(name = "Anime empty", showBackground = true)
@Composable
private fun AnimeEmptyPreview() {
    AnimeSearchPreview(
        SearchUiState(
            query = "no synthetic anime",
            category = MediaSearchCategory.ANIME,
            credentialAvailability = SearchCredentialAvailability.REQUIRED,
            content = SearchContentState.Empty
        )
    )
}

@Preview(name = "Anime rate limit", showBackground = true)
@Composable
private fun AnimeRateLimitPreview() {
    AnimeSearchPreview(
        SearchUiState(
            query = "synthetic anime",
            category = MediaSearchCategory.ANIME,
            credentialAvailability = SearchCredentialAvailability.REQUIRED,
            content = SearchContentState.Error(AppError.RateLimited)
        )
    )
}

@Composable
private fun AnimeSearchPreview(state: SearchUiState) {
    BingeeTheme {
        SearchContent(
            state = state,
            onQueryChanged = {},
            onClearQuery = {},
            onCategoryChanged = {},
            onRetryInitial = {},
            onLoadNextPage = {},
            onRetryNextPage = {},
            onOpenSettings = {}
        )
    }
}
