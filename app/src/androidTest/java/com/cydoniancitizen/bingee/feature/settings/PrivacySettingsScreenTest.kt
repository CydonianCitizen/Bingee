package com.cydoniancitizen.bingee.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import com.cydoniancitizen.bingee.testutil.TestLocaleRule
import com.cydoniancitizen.bingee.testutil.localizedTestContext
import org.junit.Rule
import org.junit.Test

class PrivacySettingsScreenTest {
    @get:Rule(order = 0)
    val localeRule = TestLocaleRule()

    private val context get() = localizedTestContext

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    @Test
    fun privacyScreenDisplaysPrivacyInformationAndCredentialEditor() {
        composeRule.setContent {
            BingeeTheme {
                PrivacySettingsContent(
                    state = PrivacyUiState(),
                    onInputChanged = {},
                    onSubmit = {},
                    onRetry = {},
                    onRequestRemoval = {},
                    onDismissRemoval = {},
                    onConfirmRemoval = {},
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.privacy_title)).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.privacy_body)
        )
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.settings_tmdb_title)
        ).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.tmdb_attribution))
            .performScrollTo()
            .assertIsDisplayed()
    }
}
