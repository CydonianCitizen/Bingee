package com.cydoniancitizen.bingee.feature.credential

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.core.credential.TmdbCredentialInputStatus
import com.cydoniancitizen.bingee.core.credential.TmdbCredentialStatus
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeDimensions
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.ui.toUiError

@Composable
internal fun CredentialEditor(
    @StringRes titleRes: Int,
    @StringRes descriptionRes: Int,
    credentialStatus: TmdbCredentialStatus,
    inputStatus: TmdbCredentialInputStatus,
    error: AppError?,
    onInputChanged: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onRetry: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var input by remember { mutableStateOf("") }
    var revealed by remember { mutableStateOf(false) }
    val isSubmitting = credentialStatus is TmdbCredentialStatus.Validating

    LaunchedEffect(credentialStatus) {
        if (credentialStatus == TmdbCredentialStatus.Valid) {
            input = ""
            revealed = false
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(BingeeDimensions.contentSpacing)
    ) {
        Text(
            text = stringResource(titleRes),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = stringResource(descriptionRes),
            style = MaterialTheme.typography.bodyLarge
        )
        CredentialStatus(status = credentialStatus)
        OutlinedTextField(
            value = input,
            onValueChange = { value ->
                input = value
                onInputChanged(value)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSubmitting,
            label = { Text(stringResource(R.string.tmdb_credential_label)) },
            placeholder = { Text(stringResource(R.string.tmdb_credential_placeholder)) },
            visualTransformation =
            if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { revealed = !revealed }) {
                    Text(
                        stringResource(
                            if (revealed) R.string.credential_hide else R.string.credential_show
                        )
                    )
                }
            },
            isError = inputStatus == TmdbCredentialInputStatus.LOCALLY_INVALID,
            supportingText = {
                if (inputStatus == TmdbCredentialInputStatus.LOCALLY_INVALID) {
                    Text(stringResource(R.string.tmdb_credential_invalid_structure))
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit(input) })
        )
        Row(horizontalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)) {
            Button(
                onClick = { onSubmit(input) },
                enabled =
                input.isNotBlank() &&
                    inputStatus == TmdbCredentialInputStatus.LOCALLY_VALID &&
                    !isSubmitting
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator()
                } else {
                    Text(stringResource(R.string.credential_validate))
                }
            }
            if (credentialStatus is TmdbCredentialStatus.TemporarilyUnverifiable) {
                TextButton(onClick = { onRetry(input) }, enabled = !isSubmitting) {
                    Text(stringResource(R.string.action_retry))
                }
            }
        }
        error?.let { safeError ->
            Text(
                text = stringResource(safeError.toUiError().messageRes),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun CredentialStatus(status: TmdbCredentialStatus) {
    val statusText =
        when (status) {
            TmdbCredentialStatus.Checking -> R.string.credential_status_checking
            TmdbCredentialStatus.NotConfigured -> R.string.credential_status_missing
            is TmdbCredentialStatus.Validating -> R.string.credential_status_validating
            TmdbCredentialStatus.Valid -> R.string.credential_status_valid
            is TmdbCredentialStatus.Rejected ->
                if (status.hasStoredCredential) {
                    R.string.credential_status_replacement_rejected
                } else {
                    R.string.credential_status_rejected
                }
            is TmdbCredentialStatus.TemporarilyUnverifiable ->
                R.string.credential_status_temporarily_unverifiable

            TmdbCredentialStatus.StorageUnreadable -> R.string.credential_status_storage_unreadable
        }
    Text(
        text = stringResource(statusText),
        color =
        if (status is TmdbCredentialStatus.Rejected ||
            status == TmdbCredentialStatus.StorageUnreadable
        ) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        style = MaterialTheme.typography.bodyMedium
    )
    if (status == TmdbCredentialStatus.Valid) {
        Text(
            text = stringResource(R.string.credential_stored_mask),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
