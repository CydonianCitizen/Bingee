package com.cydoniancitizen.bingee.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import com.cydoniancitizen.bingee.testutil.TestLocaleRule
import com.cydoniancitizen.bingee.testutil.localizedTestContext
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DataBackupSettingsScreenTest {
    @get:Rule(order = 0)
    val localeRule = TestLocaleRule()

    private val context get() = localizedTestContext

    @get:Rule(order = 1)
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

        composeRule.onNodeWithText(context.getString(R.string.settings_backup_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.backup_save)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.backup_share)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.backup_restore)).performScrollTo().assertIsDisplayed()
        composeRule.onNode(hasText(context.getString(R.string.tvtime_import_open_action)) and hasClickAction())
            .performScrollTo()
            .assertIsDisplayed()

        composeRule.onNode(
            hasText(context.getString(R.string.tvtime_import_open_action)) and hasClickAction()
        ).performClick()
        assertTrue(tvTimeOpened.get())
    }
}
