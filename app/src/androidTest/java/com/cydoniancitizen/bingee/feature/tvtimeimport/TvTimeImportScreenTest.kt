package com.cydoniancitizen.bingee.feature.tvtimeimport

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import com.cydoniancitizen.bingee.data.imports.model.ImportedSourceSummary
import com.cydoniancitizen.bingee.data.imports.model.ImportedUnsupportedFields
import com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeImportPreview
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TvTimeImportScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun idleStateExplainsExperimentalProfileAndSelectionAction() {
        val selected = AtomicBoolean(false)
        composeRule.setContent {
            BingeeTheme {
                TvTimeImportContent(
                    state = TvTimeImportUiState(),
                    onBack = {},
                    onSelectArchive = { selected.set(true) },
                    onStartMatching = {},
                    onAcceptExact = {},
                    onAcceptHigh = {},
                    onSkip = {},
                    onSelectMediaCandidate = { _, _ -> },
                    onSelectEpisodeCandidate = { _, _ -> },
                    onSearch = { _, _, _ -> },
                    onPreparePreview = {},
                    onConfirm = {},
                    onCancel = {},
                    onSetFilter = {}
                )
            }
        }

        composeRule.onNodeWithText("Experimental: supports one documented JSON ZIP format.").assertIsDisplayed()
        composeRule.onNodeWithText("Select TV Time ZIP").performClick()
        assertTrue(selected.get())
    }

    @Test
    fun sourceSummaryAndAdditivePreviewExposeSafeBoundaries() {
        var startMatching = false
        var confirm = false
        val summary = ImportedSourceSummary(
            movieRecordCount = 1,
            seriesCount = 1,
            seasonCount = 1,
            episodeCount = 2,
            watchedMovieCount = 1,
            watchedEpisodeCount = 1,
            specialsCount = 0,
            recordsWithImdbIds = 1,
            recordsWithTvdbIds = 2,
            warningCount = 1,
            invalidRecordCount = 0,
            unsupported = ImportedUnsupportedFields(favoriteRecords = 1, customLists = 1)
        )
        composeRule.setContent {
            BingeeTheme {
                TvTimeImportContent(
                    state = TvTimeImportUiState(
                        stage = TvTimeImportStage.SOURCE_SUMMARY,
                        summary = summary
                    ),
                    onBack = {},
                    onSelectArchive = {},
                    onStartMatching = { startMatching = true },
                    onAcceptExact = {},
                    onAcceptHigh = {},
                    onSkip = {},
                    onSelectMediaCandidate = { _, _ -> },
                    onSelectEpisodeCandidate = { _, _ -> },
                    onSearch = { _, _, _ -> },
                    onPreparePreview = {},
                    onConfirm = {},
                    onCancel = {},
                    onSetFilter = {}
                )
            }
        }
        composeRule.onNodeWithText("Source summary").assertIsDisplayed()
        composeRule.onNodeWithText("Movies 1 · series 1 · seasons 1 · episodes 2").assertIsDisplayed()
        composeRule.onNodeWithText("Match with TMDB").performClick()
        assertTrue(startMatching)
    }

    @Test
    fun previewRequiresExplicitConfirmationAndShowsAdditiveState() {
        var confirm = false
        composeRule.setContent {
            BingeeTheme {
                TvTimeImportContent(
                    state = TvTimeImportUiState(
                        stage = TvTimeImportStage.PREVIEW,
                        preview = TvTimeImportPreview(
                            newLibraryCount = 1,
                            existingLibraryCount = 0,
                            movieProgressToAdd = 1,
                            episodeProgressToAdd = 1,
                            timestampConflictCount = 1,
                            skippedCount = 1,
                            invalidRecordCount = 0,
                            unsupported = ImportedUnsupportedFields()
                        )
                    ),
                    onBack = {},
                    onSelectArchive = {},
                    onStartMatching = {},
                    onAcceptExact = {},
                    onAcceptHigh = {},
                    onSkip = {},
                    onSelectMediaCandidate = { _, _ -> },
                    onSelectEpisodeCandidate = { _, _ -> },
                    onSearch = { _, _, _ -> },
                    onPreparePreview = {},
                    onConfirm = { confirm = true },
                    onCancel = {},
                    onSetFilter = {}
                )
            }
        }
        composeRule.onNodeWithText("Preview additive import").assertIsDisplayed()
        composeRule.onNodeWithText("Confirm import").performClick()
        assertTrue(confirm)
        composeRule.onNodeWithText("No local data was removed.").assertDoesNotExist()
    }
}
