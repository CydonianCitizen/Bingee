package com.cydoniancitizen.bingee.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import com.cydoniancitizen.bingee.core.model.groupReleaseEvents
import com.cydoniancitizen.bingee.debug.FakeAnimeData
import java.time.LocalDate

@Preview(name = "Anime premiere", showBackground = true)
@Composable
private fun AnimePremiereHomePreview() {
    val today = LocalDate.of(2026, 8, 3)
    BingeeTheme {
        HomeContent(
            state = HomeUiState(
                content = HomeContentState.Events(
                    groupReleaseEvents(listOf(FakeAnimeData.animePremiere), today)
                ),
                today = today
            ),
            onRefresh = {},
            onRetryLocal = {},
            onDismissFeedback = {},
            onOpenSettings = {},
            onOpenDetails = { _, _ -> }
        )
    }
}
