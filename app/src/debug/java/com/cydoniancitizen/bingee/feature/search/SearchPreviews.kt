package com.cydoniancitizen.bingee.feature.search

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import com.cydoniancitizen.bingee.core.model.MediaSearchCategory
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.debug.FakeMediaData

private val noOpText: (String) -> Unit = {}
private val noOpCategory: (MediaSearchCategory) -> Unit = {}

@Preview(name = "Results", showBackground = true)
@Preview(name = "Results dark", showBackground = true, uiMode = 0x20)
@Composable
private fun ResultsPreview() {
    SearchPreview(
        SearchUiState(
            query = "fixed",
            credentialAvailability = SearchCredentialAvailability.AVAILABLE,
            content =
            SearchContentState.Results(
                FakeMediaData.searchResults,
                currentPage = 1,
                totalPages = 2,
                nextPage = NextPageState.Ready
            )
        )
    )
}

@Preview(name = "Empty", showBackground = true)
@Composable
private fun EmptyPreview() {
    SearchPreview(
        SearchUiState(
            query = "nothing",
            credentialAvailability = SearchCredentialAvailability.AVAILABLE,
            content = SearchContentState.Empty
        )
    )
}

@Preview(name = "Initial loading", showBackground = true)
@Composable
private fun LoadingPreview() {
    SearchPreview(
        SearchUiState(
            query = "fixed",
            credentialAvailability = SearchCredentialAvailability.AVAILABLE,
            content = SearchContentState.Loading
        )
    )
}

@Preview(name = "Initial error", showBackground = true)
@Composable
private fun ErrorPreview() {
    SearchPreview(
        SearchUiState(
            query = "fixed",
            credentialAvailability = SearchCredentialAvailability.AVAILABLE,
            content = SearchContentState.Error(AppError.NetworkUnavailable)
        )
    )
}

@Preview(name = "Next page loading", showBackground = true)
@Composable
private fun NextLoadingPreview() {
    SearchPreview(resultsState(NextPageState.Loading))
}

@Preview(name = "Next page error", showBackground = true)
@Composable
private fun NextErrorPreview() {
    SearchPreview(resultsState(NextPageState.Error(AppError.RemoteServiceFailure, 2)))
}

@Preview(name = "End", showBackground = true)
@Composable
private fun EndPreview() {
    SearchPreview(resultsState(NextPageState.End))
}

@Preview(name = "Missing credential", showBackground = true)
@Composable
private fun MissingCredentialPreview() {
    SearchPreview(
        SearchUiState(credentialAvailability = SearchCredentialAvailability.REQUIRED)
    )
}

private fun resultsState(nextPage: NextPageState) = SearchUiState(
    query = "fixed",
    credentialAvailability = SearchCredentialAvailability.AVAILABLE,
    content =
    SearchContentState.Results(
        FakeMediaData.searchResults,
        currentPage = 1,
        totalPages = 2,
        nextPage = nextPage
    )
)

@Composable
private fun SearchPreview(state: SearchUiState) {
    BingeeTheme {
        SearchContent(
            state = state,
            onQueryChanged = noOpText,
            onClearQuery = {},
            onCategoryChanged = noOpCategory,
            onRetryInitial = {},
            onLoadNextPage = {},
            onRetryNextPage = {},
            onOpenSettings = {}
        )
    }
}
