package com.cydoniancitizen.bingee.feature.tvtimeimport

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import com.cydoniancitizen.bingee.data.imports.model.ImportedSourceSummary
import com.cydoniancitizen.bingee.data.imports.model.ImportedUnsupportedFields
import com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeImportPreview
import com.cydoniancitizen.bingee.testutil.TestLocaleRule
import com.cydoniancitizen.bingee.testutil.localizedTestContext
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TvTimeImportScreenTest {
    @get:Rule(order = 0)
    val localeRule = TestLocaleRule()

    private val context get() = localizedTestContext

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

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

        composeRule.onNodeWithText(context.getString(R.string.tvtime_import_experimental)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.tvtime_import_select_archive)).performClick()
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
        composeRule.onNodeWithText(context.getString(R.string.tvtime_import_source_summary)).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.tvtime_import_summary_counts, 1, 1, 1, 2)
        ).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.tvtime_import_start_matching)).performClick()
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
        composeRule.onNodeWithText(context.getString(R.string.tvtime_import_preview_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.tvtime_import_confirm)).performClick()
        assertTrue(confirm)
        composeRule.onNodeWithText("No local data was removed.").assertDoesNotExist()
    }
}
