package com.cydoniancitizen.bingee.feature.profile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.LibraryEntry
import com.cydoniancitizen.bingee.core.model.LibraryProgress
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.MovieWatchState
import com.cydoniancitizen.bingee.core.model.PersonalRating
import com.cydoniancitizen.bingee.core.model.SeriesProgress
import com.cydoniancitizen.bingee.data.settings.ProfileCategory
import com.cydoniancitizen.bingee.data.settings.ProfileCollection
import com.cydoniancitizen.bingee.data.settings.ProfileViewMode
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProfileScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun seriesEntry(id: String, title: String, watched: Int, trackable: Int) = LibraryEntry(
        mediaRef = ExternalMediaRef(MediaSource.TMDB, id),
        mediaType = MediaType.SERIES,
        title = title,
        addedAt = Instant.EPOCH,
        inLibrary = true,
        progress = LibraryProgress.Series(SeriesProgress(watched, trackable, 0, 1, false))
    )

    @Test
    fun seriesProgressSubtitleUsesLocaleAwareEpisodePlurals() {
        composeRule.setContent {
            BingeeTheme {
                ProfileContent(
                    state = ProfileUiState(
                        today = LocalDate.of(2026, 8, 18),
                        isLoading = false,
                        collection = ProfileCollection.WATCHED,
                        category = ProfileCategory.TV_SERIES,
                        entries = listOf(
                            seriesEntry("single", "Single Episode Series", watched = 1, trackable = 1),
                            seriesEntry("many", "Multi Episode Series", watched = 2, trackable = 6)
                        )
                    ),
                    onCollectionSelected = {},
                    onCategorySelected = {},
                    onSortSelected = {},
                    onViewModeSelected = {},
                    onSearchQueryChanged = {},
                    onClearSearch = {},
                    onRemove = {},
                    onOpenSettings = {},
                    onOpenDetails = { _, _ -> },
                    onNavigateToSearch = {},
                    onDismissActionError = {}
                )
            }
        }

        // A fixed string reads "1 of 1 episodes watched"; the plural resource has to pick the singular.
        composeRule.onNodeWithText("1 of 1 episode watched").assertIsDisplayed()
        composeRule.onNodeWithText("2 of 6 episodes watched").assertIsDisplayed()
    }

    @Test
    fun profileTopAppBarAndSettingsNavigationWork() {
        val settingsClicked = AtomicBoolean(false)
        composeRule.setContent {
            BingeeTheme {
                ProfileContent(
                    state = ProfileUiState(today = LocalDate.of(2026, 8, 18), isLoading = false),
                    onCollectionSelected = {},
                    onCategorySelected = {},
                    onSortSelected = {},
                    onViewModeSelected = {},
                    onSearchQueryChanged = {},
                    onClearSearch = {},
                    onRemove = {},
                    onOpenSettings = { settingsClicked.set(true) },
                    onOpenDetails = { _, _ -> },
                    onNavigateToSearch = {},
                    onDismissActionError = {}
                )
            }
        }

        composeRule.onNodeWithText("Your collection").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Settings").performClick()
        assertTrue(settingsClicked.get())
    }

    @Test
    fun profileStatisticsActionOpensTheCanonicalStatisticsDestination() {
        val statisticsClicked = AtomicBoolean(false)
        composeRule.setContent {
            BingeeTheme {
                ProfileContent(
                    state = ProfileUiState(today = LocalDate.of(2026, 8, 18), isLoading = false),
                    onCollectionSelected = {},
                    onCategorySelected = {},
                    onSortSelected = {},
                    onViewModeSelected = {},
                    onSearchQueryChanged = {},
                    onClearSearch = {},
                    onRemove = {},
                    onOpenSettings = {},
                    onOpenStatistics = { statisticsClicked.set(true) },
                    onOpenDetails = { _, _ -> },
                    onNavigateToSearch = {},
                    onDismissActionError = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription("Statistics").performClick()
        assertTrue(statisticsClicked.get())
    }

    @Test
    fun collectionAndCategorySwitchingWork() {
        val selectedCollection = AtomicReference<ProfileCollection>()
        val selectedCategory = AtomicReference<ProfileCategory>()

        composeRule.setContent {
            BingeeTheme {
                ProfileContent(
                    state = ProfileUiState(today = LocalDate.of(2026, 8, 18), isLoading = false),
                    onCollectionSelected = selectedCollection::set,
                    onCategorySelected = selectedCategory::set,
                    onSortSelected = {},
                    onViewModeSelected = {},
                    onSearchQueryChanged = {},
                    onClearSearch = {},
                    onRemove = {},
                    onOpenSettings = {},
                    onOpenDetails = { _, _ -> },
                    onNavigateToSearch = {},
                    onDismissActionError = {}
                )
            }
        }

        composeRule.onNodeWithText("Watch Later").performClick()
        assertEquals(ProfileCollection.WATCH_LATER, selectedCollection.get())

        composeRule.onNodeWithText("TV Series").performScrollTo().performClick()
        assertEquals(ProfileCategory.TV_SERIES, selectedCategory.get())
    }

    @Test
    fun searchAndEmptyStateWork() {
        val searchQuery = AtomicReference("")
        val searchNavigated = AtomicBoolean(false)

        composeRule.setContent {
            BingeeTheme {
                ProfileContent(
                    state = ProfileUiState(
                        today = LocalDate.of(2026, 8, 18),
                        isLoading = false,
                        entries = emptyList()
                    ),
                    onCollectionSelected = {},
                    onCategorySelected = {},
                    onSortSelected = {},
                    onViewModeSelected = {},
                    onSearchQueryChanged = searchQuery::set,
                    onClearSearch = {},
                    onRemove = {},
                    onOpenSettings = {},
                    onOpenDetails = { _, _ -> },
                    onNavigateToSearch = { searchNavigated.set(true) },
                    onDismissActionError = {}
                )
            }
        }

        composeRule.onNodeWithText("No watched movies").assertIsDisplayed()
        composeRule.onNodeWithText("Explore in Search").performClick()
        assertTrue(searchNavigated.get())

        composeRule.onNode(hasSetTextAction()).performTextInput("Inception")
        assertEquals("Inception", searchQuery.get())
    }

    @Test
    fun gridViewDisplaysItemAndNavigatesToDetails() {
        val detailsOpened = AtomicBoolean(false)
        val testEntry = LibraryEntry(
            mediaRef = ExternalMediaRef(MediaSource.TMDB, "100"),
            mediaType = MediaType.MOVIE,
            title = "Inception",
            addedAt = Instant.EPOCH,
            progress = LibraryProgress.Movie(MovieWatchState.Watched(Instant.EPOCH)),
            personalRating = PersonalRating(9)
        )

        composeRule.setContent {
            BingeeTheme {
                ProfileContent(
                    state = ProfileUiState(
                        today = LocalDate.of(2026, 8, 18),
                        isLoading = false,
                        entries = listOf(testEntry)
                    ),
                    onCollectionSelected = {},
                    onCategorySelected = {},
                    onSortSelected = {},
                    onViewModeSelected = {},
                    onSearchQueryChanged = {},
                    onClearSearch = {},
                    onRemove = {},
                    onOpenSettings = {},
                    onOpenDetails = { _, _ -> detailsOpened.set(true) },
                    onNavigateToSearch = {},
                    onDismissActionError = {}
                )
            }
        }

        composeRule.onNodeWithText("Inception").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Open details for Inception").performClick()
        assertTrue(detailsOpened.get())
    }

    @Test
    fun listViewDisplaysItemAndSupportsActions() {
        val detailsOpened = AtomicBoolean(false)
        val itemRemoved = AtomicBoolean(false)
        val testEntry = LibraryEntry(
            mediaRef = ExternalMediaRef(MediaSource.TMDB, "200"),
            mediaType = MediaType.MOVIE,
            title = "Interstellar",
            addedAt = Instant.EPOCH,
            progress = LibraryProgress.Movie(MovieWatchState.Watched(Instant.EPOCH))
        )

        composeRule.setContent {
            BingeeTheme {
                ProfileContent(
                    state = ProfileUiState(
                        today = LocalDate.of(2026, 8, 18),
                        isLoading = false,
                        entries = listOf(testEntry)
                    ),
                    onCollectionSelected = {},
                    onCategorySelected = {},
                    onSortSelected = {},
                    onViewModeSelected = {},
                    onSearchQueryChanged = {},
                    onClearSearch = {},
                    onRemove = { itemRemoved.set(true) },
                    onOpenSettings = {},
                    onOpenDetails = { _, _ -> detailsOpened.set(true) },
                    onNavigateToSearch = {},
                    onDismissActionError = {}
                )
            }
        }

        composeRule.onNodeWithText("Interstellar").assertIsDisplayed()
        composeRule.onNodeWithText("Remove from library").performClick()
        assertTrue(itemRemoved.get())
    }
}
