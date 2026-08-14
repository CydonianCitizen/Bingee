package com.cydoniancitizen.bingee.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DataBackupSettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun backupActionsAndTvTimeImportAreReachable() {
        val tvTimeOpened = AtomicBoolean(false)

        composeRule.setContent {
            BingeeTheme {
                DataBackupSettingsContent(
                    backupState = BackupUiState(),
                    onSaveBackup = {},
                    onShareBackup = {},
                    onRestoreBackup = {},
                    onConfirmRestore = {},
                    onCancelRestore = {},
                    onDismissBackupFeedback = {},
                    onOpenTvTimeImport = { tvTimeOpened.set(true) },
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithText("Data & backup").assertIsDisplayed()
        composeRule.onNodeWithText("Save backup").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Share backup").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Restore backup").performScrollTo().assertIsDisplayed()
        composeRule.onNode(hasText("Import TV Time history") and hasClickAction())
            .performScrollTo()
            .assertIsDisplayed()

        composeRule.onNode(hasText("Import TV Time history") and hasClickAction()).performClick()
        assertTrue(tvTimeOpened.get())
    }
}
