package com.cydoniancitizen.bingee.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.core.designsystem.component.EmptyState
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeDimensions

@Composable
internal fun SearchScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SearchContent(state = state, onOpenSettings = onOpenSettings, modifier = modifier)
}

@Composable
internal fun SearchContent(state: SearchShellState, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(BingeeDimensions.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (state) {
            SearchShellState.CHECKING -> CircularProgressIndicator()
            SearchShellState.CONFIGURATION_REQUIRED -> {
                EmptyState(
                    title = stringResource(R.string.search_configuration_required_title),
                    body = stringResource(R.string.search_configuration_required_body)
                )
                Button(onClick = onOpenSettings) {
                    Text(stringResource(R.string.search_open_settings))
                }
            }

            SearchShellState.COMING_SOON ->
                EmptyState(
                    title = stringResource(R.string.search_not_implemented_title),
                    body = stringResource(R.string.search_not_implemented_body)
                )
        }
    }
}
