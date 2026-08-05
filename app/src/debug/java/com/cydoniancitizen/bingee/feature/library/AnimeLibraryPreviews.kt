package com.cydoniancitizen.bingee.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import com.cydoniancitizen.bingee.core.model.LibraryQuery
import com.cydoniancitizen.bingee.core.model.organizeLibraryEntries
import com.cydoniancitizen.bingee.debug.FakeAnimeData

@Preview(name = "Mixed TMDB/Jikan library", showBackground = true)
@Preview(name = "Mixed TMDB/Jikan library dark", showBackground = true, uiMode = 0x20)
@Composable
private fun MixedProviderLibraryPreview() {
    val query = LibraryQuery()
    val entries = organizeLibraryEntries(FakeAnimeData.mixedLibraryEntries, query)
    BingeeTheme {
        PreviewContent(
            LibraryUiState(
                query = query,
                content = LibraryContentState.Entries(entries),
                resultCount = entries.size,
                totalEntryCount = FakeAnimeData.mixedLibraryEntries.size
            )
        )
    }
}
