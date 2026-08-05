package com.cydoniancitizen.bingee.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import com.cydoniancitizen.bingee.core.model.LibraryMediaFilter
import com.cydoniancitizen.bingee.core.model.LibraryQuery
import com.cydoniancitizen.bingee.core.model.LibrarySort
import com.cydoniancitizen.bingee.core.model.LibraryStateFilter
import com.cydoniancitizen.bingee.core.model.organizeLibraryEntries
import com.cydoniancitizen.bingee.debug.FakeMediaData

@Preview(name = "Mixed ratings", showBackground = true)
@Preview(name = "Mixed ratings dark", showBackground = true, uiMode = 0x20)
@Composable
private fun MixedPreview() = PreviewLibrary(LibraryQuery())

@Preview(name = "Rating sort large font", showBackground = true, fontScale = 1.5f)
@Composable
private fun RatingSortPreview() = PreviewLibrary(LibraryQuery(sort = LibrarySort.PERSONAL_RATING))

@Preview(name = "Progress sort", showBackground = true)
@Composable
private fun ProgressSortPreview() = PreviewLibrary(LibraryQuery(sort = LibrarySort.PROGRESS))

@Preview(name = "Active combined filters", showBackground = true)
@Composable
private fun CombinedPreview() = PreviewLibrary(
    LibraryQuery(
        searchQuery = "series",
        mediaFilter = LibraryMediaFilter.TV_SERIES,
        stateFilter = LibraryStateFilter.IN_PROGRESS,
        sort = LibrarySort.TITLE
    )
)

@Preview(name = "No matching local result", showBackground = true)
@Composable
private fun NoResultsPreview() {
    BingeeTheme {
        PreviewContent(
            LibraryUiState(
                query = LibraryQuery(searchQuery = "missing"),
                content = LibraryContentState.NoResults,
                totalEntryCount = FakeMediaData.libraryEntries.size
            )
        )
    }
}

@Composable
private fun PreviewLibrary(query: LibraryQuery) {
    val entries = organizeLibraryEntries(FakeMediaData.libraryEntries, query)
    BingeeTheme {
        PreviewContent(
            LibraryUiState(
                query = query,
                content = LibraryContentState.Entries(entries),
                resultCount = entries.size,
                totalEntryCount = FakeMediaData.libraryEntries.size
            )
        )
    }
}

@Composable
internal fun PreviewContent(state: LibraryUiState) {
    LibraryContent(
        state = state,
        onSearchQueryChanged = {},
        onClearSearch = {},
        onMediaFilterChanged = {},
        onStateFilterChanged = {},
        onSortChanged = {},
        onRetry = {},
        onRemove = {},
        onDismissActionError = {}
    )
}
