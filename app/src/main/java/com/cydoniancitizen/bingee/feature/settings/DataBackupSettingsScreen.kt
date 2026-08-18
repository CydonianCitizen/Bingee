package com.cydoniancitizen.bingee.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeDimensions
import com.cydoniancitizen.bingee.data.importexport.BACKUP_MIME_TYPE
import com.cydoniancitizen.bingee.data.importexport.BackupFailureKind

@Composable
internal fun DataBackupSettingsScreen(
    onBack: () -> Unit,
    onOpenTvTimeImport: () -> Unit,
    modifier: Modifier = Modifier,
    backupViewModel: BackupViewModel = hiltViewModel()
) {
    val backupState by backupViewModel.uiState.collectAsStateWithLifecycle()
    val createBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE)
    ) { uri -> uri?.let(backupViewModel::saveTo) }
    val openBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(backupViewModel::importFrom) }

    DataBackupSettingsContent(
        backupState = backupState,
        onSaveBackup = {
            createBackupLauncher.launch("bingee-backup-${backupState.today}.json")
        },
        onShareBackup = backupViewModel::share,
        onRestoreBackup = { openBackupLauncher.launch(arrayOf(BACKUP_MIME_TYPE, "text/json", "text/plain")) },
        onConfirmRestore = backupViewModel::confirmRestore,
        onCancelRestore = backupViewModel::cancelPreview,
        onDismissBackupFeedback = backupViewModel::dismissFeedback,
        onOpenTvTimeImport = onOpenTvTimeImport,
        onBack = onBack,
        modifier = modifier
    )
}

@Composable
internal fun DataBackupSettingsContent(
    backupState: BackupUiState,
    onSaveBackup: () -> Unit,
    onShareBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    onConfirmRestore: () -> Unit,
    onCancelRestore: () -> Unit,
    onDismissBackupFeedback: () -> Unit,
    onOpenTvTimeImport: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(BingeeDimensions.screenPadding),
        verticalArrangement = Arrangement.spacedBy(BingeeDimensions.contentSpacing)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.detail_back)
                )
            }
            Text(
                text = stringResource(R.string.settings_backup_title),
                modifier = Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.headlineMedium
            )
        }

        BackupSection(
            state = backupState,
            onSave = onSaveBackup,
            onShare = onShareBackup,
            onRestore = onRestoreBackup,
            onDismiss = onDismissBackupFeedback
        )

        HorizontalDivider()

        TvTimeImportSection(onOpen = onOpenTvTimeImport)
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
