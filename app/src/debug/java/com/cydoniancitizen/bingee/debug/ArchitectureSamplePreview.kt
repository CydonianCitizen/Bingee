package com.cydoniancitizen.bingee.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.core.designsystem.component.EmptyState
import com.cydoniancitizen.bingee.core.designsystem.component.ErrorState
import com.cydoniancitizen.bingee.core.designsystem.component.LoadingState
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeDimensions
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.ui.toUiError

@Composable
private fun ArchitectureSampleContent(
    state: ArchitectureSampleUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (state) {
        ArchitectureSampleUiState.Initial ->
            EmptyState(
                title = stringResource(R.string.debug_architecture_sample),
                body = stringResource(R.string.debug_architecture_sample_body),
                modifier = modifier
            )
        ArchitectureSampleUiState.Loading ->
            LoadingState(
                message = stringResource(R.string.debug_loading_sample),
                modifier = modifier
            )
        is ArchitectureSampleUiState.Content ->
            Column(
                modifier = modifier,
                verticalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)
            ) {
                state.results.forEach { result ->
                    EmptyState(title = result.title)
                }
            }
        is ArchitectureSampleUiState.Failure -> {
            val uiError = state.error.toUiError()
            ErrorState(
                title = stringResource(R.string.debug_sample_error_title),
                message = stringResource(uiError.messageRes),
                retryLabel = if (uiError.canRetry) stringResource(R.string.debug_retry) else null,
                onRetry = if (uiError.canRetry) onRetry else null,
                modifier = modifier
            )
        }
    }
}

@Preview(name = "Deterministic content", showBackground = true)
@Composable
private fun ArchitectureSampleContentPreview() {
    BingeeTheme {
        ArchitectureSampleContent(
            state = ArchitectureSampleUiState.Content(FakeMediaData.searchResults),
            onRetry = {}
        )
    }
}

@Preview(name = "Deterministic failure", showBackground = true)
@Composable
private fun ArchitectureSampleFailurePreview() {
    BingeeTheme {
        ArchitectureSampleContent(
            state = ArchitectureSampleUiState.Failure(AppError.NetworkUnavailable),
            onRetry = {}
        )
    }
}
