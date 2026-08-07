package com.cydoniancitizen.bingee.feature.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeDimensions
import com.cydoniancitizen.bingee.data.importexport.BACKUP_MIME_TYPE
import com.cydoniancitizen.bingee.data.importexport.BackupFailureKind
import com.cydoniancitizen.bingee.data.settings.AppLanguage
import com.cydoniancitizen.bingee.data.settings.AppTheme
import com.cydoniancitizen.bingee.feature.credential.CredentialEditor
import java.time.Clock
import java.time.LocalDate

@Composable
internal fun SettingsScreen(
    modifier: Modifier = Modifier,
    onOpenTvTimeImport: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
    backupViewModel: BackupViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val backupState by backupViewModel.uiState.collectAsStateWithLifecycle()
    val createBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE)
    ) { uri -> uri?.let(backupViewModel::saveTo) }
    val openBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(backupViewModel::importFrom) }
    SettingsContent(
        state = state,
        onInputChanged = viewModel::onInputChanged,
        onSubmit = viewModel::submit,
        onRetry = viewModel::retry,
        onRequestRemoval = viewModel::requestRemoval,
        onDismissRemoval = viewModel::dismissRemoval,
        onConfirmRemoval = viewModel::confirmRemoval,
        onSetTheme = viewModel::setTheme,
        onSetLanguage = viewModel::setLanguage,
        backupState = backupState,
        onSaveBackup = {
            createBackupLauncher.launch("bingee-backup-${LocalDate.now(Clock.systemUTC())}.json")
        },
        onShareBackup = backupViewModel::share,
        onRestoreBackup = { openBackupLauncher.launch(arrayOf(BACKUP_MIME_TYPE, "text/json", "text/plain")) },
        onConfirmRestore = backupViewModel::confirmRestore,
        onCancelRestore = backupViewModel::cancelPreview,
        onDismissBackupFeedback = backupViewModel::dismissFeedback,
        onOpenTvTimeImport = onOpenTvTimeImport,
        modifier = modifier
    )
}

@Composable
internal fun SettingsContent(
    state: SettingsUiState,
    onInputChanged: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onRetry: (String) -> Unit,
    onRequestRemoval: () -> Unit,
    onDismissRemoval: () -> Unit,
    onConfirmRemoval: () -> Unit,
    onSetTheme: (AppTheme) -> Unit = {},
    onSetLanguage: (AppLanguage) -> Unit = {},
    backupState: BackupUiState = BackupUiState(),
    onSaveBackup: () -> Unit = {},
    onShareBackup: () -> Unit = {},
    onRestoreBackup: () -> Unit = {},
    onConfirmRestore: () -> Unit = {},
    onCancelRestore: () -> Unit = {},
    onDismissBackupFeedback: () -> Unit = {},
    onOpenTvTimeImport: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var expandedDropdown by remember { mutableStateOf<SettingsDropdownKind?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(BingeeDimensions.screenPadding),
        verticalArrangement = Arrangement.spacedBy(BingeeDimensions.contentSpacing)
    ) {
        Text(
            text = stringResource(R.string.nav_settings),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineMedium
        )
        AppearanceSection(
            theme = state.theme,
            onSetTheme = onSetTheme,
            expanded = expandedDropdown == SettingsDropdownKind.THEME,
            onExpandedChange = { isExpanded ->
                expandedDropdown = if (isExpanded) SettingsDropdownKind.THEME else null
            }
        )
        HorizontalDivider()
        LanguageSection(
            language = state.language,
            onSetLanguage = onSetLanguage,
            expanded = expandedDropdown == SettingsDropdownKind.LANGUAGE,
            onExpandedChange = { isExpanded ->
                expandedDropdown = if (isExpanded) SettingsDropdownKind.LANGUAGE else null
            }
        )
        HorizontalDivider()
        CredentialEditor(
            titleRes = R.string.settings_tmdb_title,
            descriptionRes = R.string.settings_tmdb_description,
            credentialStatus = state.credentialStatus,
            inputStatus = state.inputStatus,
            error = state.error,
            onInputChanged = onInputChanged,
            onSubmit = onSubmit,
            onRetry = onRetry
        )
        Button(
            onClick = onRequestRemoval,
            enabled = state.canRemove && !state.isSubmitting && !state.isRemoving
        ) {
            Text(stringResource(R.string.credential_remove))
        }
        Text(
            text = stringResource(R.string.credential_remove_explanation),
            style = MaterialTheme.typography.bodyMedium
        )
        HorizontalDivider()
        BackupSection(
            state = backupState,
            onSave = onSaveBackup,
            onShare = onShareBackup,
            onRestore = onRestoreBackup,
            onDismiss = onDismissBackupFeedback
        )
        TvTimeImportSection(onOpen = onOpenTvTimeImport)
        HorizontalDivider()
        OpenSourceSection()
        HorizontalDivider()
        Text(
            text = stringResource(R.string.about_title),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineSmall
        )
        Image(
            painter = painterResource(R.drawable.tmdb_logo),
            contentDescription = stringResource(R.string.tmdb_logo_description),
            modifier = Modifier.height(48.dp)
        )
        Text(stringResource(R.string.tmdb_attribution))
        Text(stringResource(R.string.privacy_summary))
        Text(stringResource(R.string.privacy_documentation_reference))
    }

    if (state.showRemovalConfirmation) {
        AlertDialog(
            onDismissRequest = onDismissRemoval,
            title = { Text(stringResource(R.string.credential_remove_confirm_title)) },
            text = { Text(stringResource(R.string.credential_remove_confirm_body)) },
            confirmButton = {
                TextButton(onClick = onConfirmRemoval) {
                    Text(stringResource(R.string.credential_remove))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRemoval) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    val preview = backupState.preview
    if (backupState.operation == BackupOperation.PREVIEW_READY && preview != null) {
        AlertDialog(
            onDismissRequest = onCancelRestore,
            title = { Text(stringResource(R.string.backup_replace_title)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(BingeeDimensions.contentSpacing)
                ) {
                    Text(
                        stringResource(
                            R.string.backup_preview_counts,
                            preview.mediaCount,
                            preview.movieCount,
                            preview.seriesCount
                        )
                    )
                    Text(
                        stringResource(
                            R.string.backup_preview_personal_counts,
                            preview.libraryCount,
                            preview.watchedMovieCount,
                            preview.watchedEpisodeCount,
                            preview.ratingCount
                        )
                    )
                    Text(stringResource(R.string.backup_preview_current_library, preview.currentLibraryCount))
                    Text(stringResource(R.string.backup_replace_warning))
                    Text(stringResource(R.string.backup_preserved_warning))
                    Text(stringResource(R.string.backup_plaintext_warning))
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirmRestore) {
                    Text(stringResource(R.string.backup_replace_local_data))
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelRestore) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

private enum class SettingsDropdownKind { THEME, LANGUAGE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceSection(
    theme: AppTheme,
    onSetTheme: (AppTheme) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit
) {
    val themeOptions = listOf(
        AppTheme.SYSTEM_DEFAULT to stringResource(R.string.settings_theme_system),
        AppTheme.LIGHT to stringResource(R.string.settings_theme_light),
        AppTheme.DARK to stringResource(R.string.settings_theme_dark)
    )
    val currentThemeLabel = stringResource(
        when (theme) {
            AppTheme.SYSTEM_DEFAULT -> R.string.settings_theme_system
            AppTheme.LIGHT -> R.string.settings_theme_light
            AppTheme.DARK -> R.string.settings_theme_dark
        }
    )

    Column(verticalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)) {
        Text(
            text = stringResource(R.string.settings_appearance_title),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineSmall
        )
        Text(stringResource(R.string.settings_theme_title), style = MaterialTheme.typography.titleMedium)
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = onExpandedChange,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = currentThemeLabel,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { onExpandedChange(false) }
            ) {
                themeOptions.forEach { (optionTheme, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onSetTheme(optionTheme)
                            onExpandedChange(false)
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSection(
    language: AppLanguage,
    onSetLanguage: (AppLanguage) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit
) {
    val languageOptions = listOf(
        AppLanguage.ENGLISH to stringResource(R.string.settings_language_en),
        AppLanguage.ITALIAN to stringResource(R.string.settings_language_it)
    )
    val currentLanguageLabel = stringResource(
        when (language) {
            AppLanguage.ENGLISH -> R.string.settings_language_en
            AppLanguage.ITALIAN -> R.string.settings_language_it
        }
    )

    Column(verticalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)) {
        Text(
            text = stringResource(R.string.settings_language_title),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineSmall
        )
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = onExpandedChange,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = currentLanguageLabel,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { onExpandedChange(false) }
            ) {
                languageOptions.forEach { (optionLang, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onSetLanguage(optionLang)
                            onExpandedChange(false)
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }
    }
}

@Composable
private fun OpenSourceSection() {
    val context = LocalContext.current
    val repositoryUrl = "https://github.com/CydoniaCitizen/Bingee"
    Column(verticalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)) {
        Text(
            text = stringResource(R.string.settings_open_source_title),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = stringResource(R.string.settings_open_source_description),
            style = MaterialTheme.typography.bodyMedium
        )
        Button(
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(repositoryUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(intent)
                } catch (_: Exception) {
                    // Safe handling when no browser resolves intent
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.settings_open_source_github_action))
        }
    }
}

@Composable
private fun TvTimeImportSection(onOpen: () -> Unit) {
    Text(
        text = stringResource(R.string.tvtime_import_title),
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.headlineSmall
    )
    Text(stringResource(R.string.tvtime_import_experimental))
    Text(stringResource(R.string.tvtime_import_privacy), style = MaterialTheme.typography.bodySmall)
    Text(stringResource(R.string.tvtime_import_limitations), style = MaterialTheme.typography.bodySmall)
    Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.tvtime_import_open_action))
    }
}

@Composable
private fun BackupSection(
    state: BackupUiState,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onRestore: () -> Unit,
    onDismiss: () -> Unit
) {
    val canStart = state.operation == BackupOperation.IDLE ||
        state.operation == BackupOperation.SUCCESS ||
        state.operation == BackupOperation.FAILURE
    val isBusy = state.operation == BackupOperation.SAVING ||
        state.operation == BackupOperation.SHARING ||
        state.operation == BackupOperation.READING ||
        state.operation == BackupOperation.VALIDATING ||
        state.operation == BackupOperation.RESTORING
    val operationProgressDescription = stringResource(R.string.backup_operation_in_progress)
    Text(
        text = stringResource(R.string.settings_backup_title),
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.headlineSmall
    )
    Text(stringResource(R.string.settings_backup_description))
    Text(stringResource(R.string.backup_plaintext_warning), style = MaterialTheme.typography.bodySmall)
    Column(verticalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)) {
        Button(onClick = onSave, enabled = canStart, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.backup_save))
        }
        Button(onClick = onShare, enabled = canStart, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.backup_share))
        }
    }
    Button(onClick = onRestore, enabled = canStart, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.backup_restore))
    }
    if (isBusy) {
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    stateDescription = operationProgressDescription
                    liveRegion = LiveRegionMode.Polite
                }
        )
    }
    state.failure?.let { failure ->
        Text(
            text = stringResource(failure.toStringRes()),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            color = if (failure == BackupFailureKind.SCHEDULING_WARNING) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.error
            }
        )
        if (state.operation == BackupOperation.FAILURE) {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_dismiss)) }
        }
    }
    if (state.operation == BackupOperation.SUCCESS && state.failure == null) {
        Text(stringResource(R.string.backup_success))
    }
}

private fun BackupFailureKind.toStringRes(): Int = when (this) {
    BackupFailureKind.UNREADABLE -> R.string.backup_error_unreadable
    BackupFailureKind.TOO_LARGE -> R.string.backup_error_too_large
    BackupFailureKind.INVALID_UTF8,
    BackupFailureKind.MALFORMED_JSON,
    BackupFailureKind.INVALID_STRUCTURE -> R.string.backup_error_malformed
    BackupFailureKind.WRONG_FORMAT -> R.string.backup_error_wrong_format
    BackupFailureKind.MISSING_VERSION -> R.string.backup_error_missing_version
    BackupFailureKind.UNSUPPORTED_VERSION -> R.string.backup_error_unsupported_version
    BackupFailureKind.VALIDATION -> R.string.backup_error_validation
    BackupFailureKind.DUPLICATE_IDENTITY -> R.string.backup_error_duplicate
    BackupFailureKind.MISSING_REFERENCE -> R.string.backup_error_missing_reference
    BackupFailureKind.CONFLICTING_REFERENCE -> R.string.backup_error_conflicting_reference
    BackupFailureKind.WRITE_FAILED -> R.string.backup_error_write
    BackupFailureKind.TRANSACTION_FAILED -> R.string.backup_error_transaction
    BackupFailureKind.SCHEDULING_WARNING -> R.string.backup_warning_schedule
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
