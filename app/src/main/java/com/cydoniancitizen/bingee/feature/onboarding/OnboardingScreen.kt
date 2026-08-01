package com.cydoniancitizen.bingee.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeDimensions
import com.cydoniancitizen.bingee.feature.credential.CredentialEditor

@Composable
internal fun OnboardingRoute(
    onConfigured: () -> Unit,
    onContinueOffline: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    OnboardingScreen(
        state = state,
        onInputChanged = viewModel::onInputChanged,
        onSubmit = viewModel::submit,
        onRetry = viewModel::submit,
        onConfigured = onConfigured,
        onContinueOffline = onContinueOffline
    )
}

@Composable
internal fun OnboardingScreen(
    state: OnboardingUiState,
    onInputChanged: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onRetry: (String) -> Unit,
    onConfigured: () -> Unit,
    onContinueOffline: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier =
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(BingeeDimensions.screenPadding),
        verticalArrangement = Arrangement.spacedBy(BingeeDimensions.contentSpacing)
    ) {
        CredentialEditor(
            titleRes = R.string.onboarding_title,
            descriptionRes = R.string.onboarding_description,
            credentialStatus = state.credentialStatus,
            inputStatus = state.inputStatus,
            error = state.error,
            onInputChanged = onInputChanged,
            onSubmit = onSubmit,
            onRetry = onRetry
        )
        Text(
            text = stringResource(R.string.onboarding_no_backend),
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
        )
        if (state.configured) {
            Button(onClick = onConfigured) {
                Text(stringResource(R.string.onboarding_continue_configured))
            }
        }
        TextButton(onClick = onContinueOffline, enabled = !state.isSubmitting) {
            Text(stringResource(R.string.onboarding_continue_offline))
        }
    }
}
