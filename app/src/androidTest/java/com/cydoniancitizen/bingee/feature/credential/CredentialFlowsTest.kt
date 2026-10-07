package com.cydoniancitizen.bingee.feature.credential

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.core.credential.TmdbCredentialInputStatus
import com.cydoniancitizen.bingee.core.credential.TmdbCredentialStatus
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.feature.onboarding.OnboardingScreen
import com.cydoniancitizen.bingee.feature.onboarding.OnboardingUiState
import com.cydoniancitizen.bingee.feature.search.SearchContent
import com.cydoniancitizen.bingee.feature.search.SearchCredentialAvailability
import com.cydoniancitizen.bingee.feature.search.SearchUiState
import com.cydoniancitizen.bingee.feature.settings.PrivacySettingsContent
import com.cydoniancitizen.bingee.feature.settings.PrivacyUiState
import com.cydoniancitizen.bingee.testutil.TestLocaleRule
import com.cydoniancitizen.bingee.testutil.localizedTestContext
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CredentialFlowsTest {
    @get:Rule(order = 0)
    val localeRule = TestLocaleRule()

    private val context get() = localizedTestContext

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    @Test
    fun credentialIsMaskedByDefaultAndCanBeRevealedAndHidden() {
        composeRule.setContent {
            BingeeTheme {
                CredentialEditor(
                    titleRes = R.string.settings_tmdb_title,
                    descriptionRes = R.string.settings_tmdb_description,
                    credentialStatus = TmdbCredentialStatus.NotConfigured,
                    inputStatus = TmdbCredentialInputStatus.LOCALLY_VALID,
                    error = null,
                    onInputChanged = {},
                    onSubmit = {},
                    onRetry = {}
                )
            }
        }

        composeRule.onNode(hasSetTextAction()).performTextInput("fake_ui_token")
        composeRule
            .onNode(hasSetTextAction())
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))

        composeRule.onNodeWithText(context.getString(R.string.credential_show)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.credential_hide)).assertIsDisplayed()
        composeRule
            .onNode(hasSetTextAction())
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))

        composeRule.onNodeWithText(context.getString(R.string.credential_hide)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.credential_show)).assertIsDisplayed()
        composeRule
            .onNode(hasSetTextAction())
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
    }

    @Test
    fun invalidInputDisablesSubmission() {
        composeRule.setContent {
            BingeeTheme {
                CredentialEditor(
                    titleRes = R.string.settings_tmdb_title,
                    descriptionRes = R.string.settings_tmdb_description,
                    credentialStatus = TmdbCredentialStatus.NotConfigured,
                    inputStatus = TmdbCredentialInputStatus.LOCALLY_INVALID,
                    error = AppError.InvalidInput,
                    onInputChanged = {},
                    onSubmit = {},
                    onRetry = {}
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.credential_validate)).assertIsNotEnabled()
        composeRule.onNodeWithText(context.getString(R.string.error_invalid_input)).assertIsDisplayed()
    }

    @Test
    fun loadingErrorRetryAndSuccessStatesAreVisible() {
        var retried by mutableStateOf(false)
        composeRule.setContent {
            BingeeTheme {
                CredentialEditor(
                    titleRes = R.string.settings_tmdb_title,
                    descriptionRes = R.string.settings_tmdb_description,
                    credentialStatus =
                    if (retried) {
                        TmdbCredentialStatus.Valid
                    } else {
                        TmdbCredentialStatus.TemporarilyUnverifiable(
                            AppError.NetworkUnavailable,
                            hasStoredCredential = false
                        )
                    },
                    inputStatus = TmdbCredentialInputStatus.EMPTY,
                    error = if (retried) null else AppError.NetworkUnavailable,
                    onInputChanged = {},
                    onSubmit = {},
                    onRetry = { retried = true }
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.action_retry)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.credential_status_valid)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.credential_stored_mask)).assertIsDisplayed()
    }

    @Test
    fun validationLoadingStateIsShown() {
        composeRule.setContent {
            BingeeTheme {
                CredentialEditor(
                    titleRes = R.string.settings_tmdb_title,
                    descriptionRes = R.string.settings_tmdb_description,
                    credentialStatus = TmdbCredentialStatus.Validating(false),
                    inputStatus = TmdbCredentialInputStatus.LOCALLY_VALID,
                    error = null,
                    onInputChanged = {},
                    onSubmit = {},
                    onRetry = {}
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.credential_status_validating)).assertIsDisplayed()
    }

    @Test
    fun onboardingOffersSuccessAndOfflineContinuation() {
        val configured = AtomicBoolean(false)
        val offline = AtomicBoolean(false)
        composeRule.setContent {
            BingeeTheme {
                OnboardingScreen(
                    state =
                    OnboardingUiState(
                        credentialStatus = TmdbCredentialStatus.Valid,
                        configured = true
                    ),
                    onInputChanged = {},
                    onSubmit = {},
                    onRetry = {},
                    onConfigured = { configured.set(true) },
                    onContinueOffline = { offline.set(true) }
                )
            }
        }

        composeRule.onNodeWithText(
            context.getString(R.string.onboarding_continue_configured)
        ).performScrollTo().performClick()
        composeRule.onNodeWithText(
            context.getString(R.string.onboarding_continue_offline)
        ).performScrollTo().performClick()

        assertTrue(configured.get())
        assertTrue(offline.get())
    }

    @Test
    fun settingsRemovalRequiresIntentionalAction() {
        val requested = AtomicBoolean(false)
        composeRule.setContent {
            BingeeTheme {
                PrivacySettingsContent(
                    state = PrivacyUiState(credentialStatus = TmdbCredentialStatus.Valid),
                    onInputChanged = {},
                    onSubmit = {},
                    onRetry = {},
                    onRequestRemoval = { requested.set(true) },
                    onDismissRemoval = {},
                    onConfirmRemoval = {},
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.credential_remove)).performScrollTo().performClick()

        assertTrue(requested.get())
    }

    @Test
    fun searchMissingCredentialRoutesToSettings() {
        val opened = AtomicBoolean(false)
        composeRule.setContent {
            BingeeTheme {
                SearchContent(
                    state =
                    SearchUiState(
                        credentialAvailability = SearchCredentialAvailability.REQUIRED
                    ),
                    onQueryChanged = {},
                    onClearQuery = {},
                    onCategoryChanged = {},
                    onRetryInitial = {},
                    onLoadNextPage = {},
                    onRetryNextPage = {},
                    onOpenSettings = { opened.set(true) }
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.search_open_settings)).performClick()

        assertTrue(opened.get())
    }
}
