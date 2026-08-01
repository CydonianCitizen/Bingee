package com.cydoniancitizen.bingee.feature.search

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.core.designsystem.component.EmptyState
import com.cydoniancitizen.bingee.core.designsystem.component.ErrorState
import com.cydoniancitizen.bingee.core.designsystem.component.LoadingState
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeDimensions
import com.cydoniancitizen.bingee.core.model.MediaSearchCategory
import com.cydoniancitizen.bingee.core.model.MediaSearchResult
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.ui.toUiError

@Composable
internal fun SearchScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SearchContent(
        state = state,
        onQueryChanged = viewModel::onQueryChanged,
        onClearQuery = viewModel::clearQuery,
        onCategoryChanged = viewModel::onCategoryChanged,
        onRetryInitial = viewModel::retryInitialSearch,
        onLoadNextPage = viewModel::loadNextPage,
        onRetryNextPage = viewModel::retryNextPage,
        onOpenSettings = onOpenSettings,
        modifier = modifier
    )
}

@Composable
internal fun SearchContent(
    state: SearchUiState,
    onQueryChanged: (String) -> Unit,
    onClearQuery: () -> Unit,
    onCategoryChanged: (MediaSearchCategory) -> Unit,
    onRetryInitial: () -> Unit,
    onLoadNextPage: () -> Unit,
    onRetryNextPage: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(BingeeDimensions.screenPadding),
        verticalArrangement = Arrangement.spacedBy(BingeeDimensions.contentSpacing)
    ) {
        Text(
            text = stringResource(R.string.search_title),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineMedium
        )
        when (state.credentialAvailability) {
            SearchCredentialAvailability.CHECKING ->
                LoadingState(stringResource(R.string.search_checking_configuration))

            SearchCredentialAvailability.REQUIRED ->
                ConfigurationRequired(onOpenSettings = onOpenSettings)

            SearchCredentialAvailability.AVAILABLE -> {
                SearchControls(
                    query = state.query,
                    category = state.category,
                    onQueryChanged = onQueryChanged,
                    onClearQuery = onClearQuery,
                    onCategoryChanged = onCategoryChanged
                )
                SearchBody(
                    content = state.content,
                    onRetryInitial = onRetryInitial,
                    onLoadNextPage = onLoadNextPage,
                    onRetryNextPage = onRetryNextPage,
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ConfigurationRequired(onOpenSettings: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)
    ) {
        EmptyState(
            title = stringResource(R.string.search_configuration_required_title),
            body = stringResource(R.string.search_configuration_required_body)
        )
        Button(onClick = onOpenSettings) {
            Text(stringResource(R.string.search_open_settings))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchControls(
    query: String,
    category: MediaSearchCategory,
    onQueryChanged: (String) -> Unit,
    onClearQuery: () -> Unit,
    onCategoryChanged: (MediaSearchCategory) -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChanged,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.search_query_label)) },
        placeholder = { Text(stringResource(R.string.search_query_placeholder)) },
        trailingIcon =
        if (query.isNotEmpty()) {
            {
                IconButton(onClick = onClearQuery) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.search_clear_query)
                    )
                }
            }
        } else {
            null
        },
        singleLine = true
    )
    val categories = MediaSearchCategory.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        categories.forEachIndexed { index, item ->
            SegmentedButton(
                selected = category == item,
                onClick = { onCategoryChanged(item) },
                shape = SegmentedButtonDefaults.itemShape(index, categories.size),
                label = {
                    Text(
                        stringResource(
                            if (item == MediaSearchCategory.MOVIES) {
                                R.string.search_category_movies
                            } else {
                                R.string.search_category_tv
                            }
                        )
                    )
                }
            )
        }
    }
}

@Composable
private fun SearchBody(
    content: SearchContentState,
    onRetryInitial: () -> Unit,
    onLoadNextPage: () -> Unit,
    onRetryNextPage: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        when (content) {
            SearchContentState.Idle ->
                EmptyState(
                    title = stringResource(R.string.search_idle_title),
                    body = stringResource(R.string.search_idle_body)
                )

            SearchContentState.Loading ->
                LoadingState(stringResource(R.string.search_loading))

            SearchContentState.Empty ->
                EmptyState(
                    title = stringResource(R.string.search_empty_title),
                    body = stringResource(R.string.search_empty_body)
                )

            is SearchContentState.Error ->
                InitialSearchError(
                    error = content.error,
                    onRetry = onRetryInitial,
                    onOpenSettings = onOpenSettings
                )

            is SearchContentState.Results ->
                SearchResults(
                    content = content,
                    onLoadNextPage = onLoadNextPage,
                    onRetryNextPage = onRetryNextPage,
                    onOpenSettings = onOpenSettings
                )
        }
    }
}

@Composable
private fun InitialSearchError(error: AppError, onRetry: () -> Unit, onOpenSettings: () -> Unit) {
    val uiError = error.toUiError()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)
    ) {
        ErrorState(
            title = stringResource(R.string.search_error_title),
            message = stringResource(uiError.messageRes),
            retryLabel = if (uiError.canRetry) stringResource(R.string.action_retry) else null,
            onRetry = if (uiError.canRetry) onRetry else null
        )
        if (error == AppError.Unauthorized) {
            Button(onClick = onOpenSettings) {
                Text(stringResource(R.string.search_open_settings))
            }
        }
    }
}

@Composable
private fun SearchResults(
    content: SearchContentState.Results,
    onLoadNextPage: () -> Unit,
    onRetryNextPage: () -> Unit,
    onOpenSettings: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)
    ) {
        items(
            items = content.items,
            key = { "${it.externalRef.source}:${it.externalRef.externalId}" }
        ) { result ->
            SearchResultItem(result)
        }
        item {
            when (val next = content.nextPage) {
                NextPageState.Ready ->
                    Button(
                        onClick = onLoadNextPage,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.search_load_more))
                    }

                NextPageState.Loading ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(BingeeDimensions.elementSpacing),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = stringResource(R.string.search_loading_more),
                            modifier = Modifier.padding(start = BingeeDimensions.elementSpacing)
                        )
                    }

                NextPageState.End ->
                    Text(
                        text = stringResource(R.string.search_end_of_results),
                        modifier = Modifier.fillMaxWidth().padding(BingeeDimensions.elementSpacing),
                        style = MaterialTheme.typography.bodyMedium
                    )

                is NextPageState.Error -> {
                    val uiError = next.error.toUiError()
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)
                    ) {
                        Text(stringResource(uiError.messageRes))
                        if (uiError.canRetry) {
                            Button(onClick = onRetryNextPage) {
                                Text(stringResource(R.string.search_retry_more))
                            }
                        }
                        if (next.error == AppError.Unauthorized) {
                            Button(onClick = onOpenSettings) {
                                Text(stringResource(R.string.search_open_settings))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SearchResultItem(result: MediaSearchResult, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(BingeeDimensions.elementSpacing),
            horizontalArrangement = Arrangement.spacedBy(BingeeDimensions.contentSpacing)
        ) {
            SearchPoster(result)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)
            ) {
                Text(
                    text = result.title,
                    style = MaterialTheme.typography.titleMedium
                )
                result.originalTitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                result.releaseDate?.let {
                    Text(
                        text = stringResource(R.string.search_release_year, it.year),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                result.overview?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun SearchPoster(result: MediaSearchResult) {
    val posterModifier =
        Modifier
            .width(96.dp)
            .height(144.dp)
            .clip(MaterialTheme.shapes.medium)
    val placeholder = painterResource(R.drawable.poster_placeholder)
    if (result.posterUrl == null) {
        Image(
            painter = placeholder,
            contentDescription = stringResource(R.string.search_no_poster, result.title),
            contentScale = ContentScale.Crop,
            modifier = posterModifier
        )
    } else {
        AsyncImage(
            model = result.posterUrl,
            contentDescription = stringResource(R.string.search_poster_description, result.title),
            placeholder = placeholder,
            error = placeholder,
            fallback = placeholder,
            contentScale = ContentScale.Crop,
            modifier = posterModifier
        )
    }
}
