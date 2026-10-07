package com.cydoniancitizen.bingee.feature.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.cydoniancitizen.bingee.R
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
import com.cydoniancitizen.bingee.data.settings.ProfileDisplayModes
import com.cydoniancitizen.bingee.data.settings.ProfileViewMode
import com.cydoniancitizen.bingee.testutil.TestLocaleRule
import com.cydoniancitizen.bingee.testutil.localizedTestContext
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProfileScreenTest {
    @get:Rule(order = 0)
    val localeRule = TestLocaleRule()

    private val context get() = localizedTestContext

    @get:Rule(order = 1)
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
        val italian = context.resources.configuration.locales[0].language == "it"
        assertEquals(
            if (italian) "1 di 1 episodio visto" else "1 of 1 episode watched",
            context.resources.getQuantityString(R.plurals.library_progress_episodes, 1, 1, 1)
        )
        assertEquals(
            if (italian) "2 di 6 episodi visti" else "2 of 6 episodes watched",
            context.resources.getQuantityString(R.plurals.library_progress_episodes, 6, 2, 6)
        )
        composeRule.onNodeWithText(
            context.resources.getQuantityString(R.plurals.library_progress_episodes, 1, 1, 1)
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.resources.getQuantityString(R.plurals.library_progress_episodes, 6, 2, 6)
        ).assertIsDisplayed()
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

        composeRule.onNodeWithText(context.getString(R.string.profile_collection_title)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.nav_settings)).performClick()
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

        composeRule.onNodeWithContentDescription(context.getString(R.string.statistics_title)).performClick()
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

        composeRule.onNodeWithText(context.getString(R.string.profile_tab_watch_later)).performClick()
        assertEquals(ProfileCollection.WATCH_LATER, selectedCollection.get())

        composeRule.onNodeWithText(context.getString(R.string.profile_tab_tv_series)).performScrollTo().performClick()
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

        composeRule.onNodeWithText(context.getString(R.string.profile_empty_watched_movies_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.profile_empty_action_search)).performClick()
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
        composeRule.onNodeWithContentDescription(context.getString(R.string.open_details, "Inception")).performClick()
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
        composeRule.onNodeWithText(context.getString(R.string.library_action_remove)).performClick()
        assertTrue(itemRemoved.get())
    }

    @Test
    fun gridFavoriteActionExposesItsCurrentToggleState() = assertFavoriteToggle(ProfileViewMode.GRID)

    @Test
    fun listFavoriteActionExposesItsCurrentToggleState() = assertFavoriteToggle(ProfileViewMode.LIST)

    private fun assertFavoriteToggle(mode: ProfileViewMode) {
        val context = localizedTestContext
        val add = context.getString(R.string.favorite_add)
        val remove = context.getString(R.string.favorite_remove)
        var favorite by mutableStateOf(false)
        val testEntry = LibraryEntry(
            mediaRef = ExternalMediaRef(MediaSource.TMDB, "300"),
            mediaType = MediaType.MOVIE,
            title = "Favorite candidate",
            addedAt = Instant.EPOCH,
            isFavorite = false
        )

        composeRule.setContent {
            BingeeTheme {
                ProfileContent(
                    state = ProfileUiState(
                        today = LocalDate.of(2026, 8, 18),
                        isLoading = false,
                        displayModes = ProfileDisplayModes(watchedMovies = mode),
                        entries = listOf(testEntry.copy(isFavorite = favorite))
                    ),
                    onCollectionSelected = {},
                    onCategorySelected = {},
                    onSortSelected = {},
                    onViewModeSelected = {},
                    onSearchQueryChanged = {},
                    onClearSearch = {},
                    onRemove = {},
                    onToggleFavorite = { favorite = !it.isFavorite },
                    onOpenSettings = {},
                    onOpenDetails = { _, _ -> },
                    onNavigateToSearch = {},
                    onDismissActionError = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription(add)
            .assertIsDisplayed()
            .assert(isToggleable())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
            .assertIsOff()
            .performClick()
        composeRule.onNodeWithContentDescription(remove).assertIsOn()
        if (mode == ProfileViewMode.GRID) {
            composeRule.onNodeWithContentDescription(context.getString(R.string.profile_more_actions)).performClick()
            composeRule.onNodeWithText(remove)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
                .performClick()
        } else {
            composeRule.onNodeWithContentDescription(remove).performClick()
        }
        composeRule.onNodeWithContentDescription(add).assertIsOff()
        assertTrue(!favorite)
    }
}
