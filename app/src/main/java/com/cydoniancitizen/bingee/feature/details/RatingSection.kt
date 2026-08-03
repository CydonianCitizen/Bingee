package com.cydoniancitizen.bingee.feature.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeDimensions
import com.cydoniancitizen.bingee.core.model.PersonalRating
import com.cydoniancitizen.bingee.core.ui.toUiError
import kotlin.math.roundToInt

@Composable
internal fun RatingSection(
    state: DetailRatingState,
    onSelect: (Int) -> Unit,
    onSave: () -> Unit,
    onRemove: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)
    ) {
        Text(
            text = stringResource(R.string.detail_rating_title),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLarge
        )
        when (state) {
            DetailRatingState.Loading -> Text(stringResource(R.string.detail_rating_loading))
            is DetailRatingState.Error -> Text(
                text = stringResource(state.error.toUiError().messageRes),
                color = MaterialTheme.colorScheme.error
            )
            is DetailRatingState.Ready -> {
                val currentDescription = state.rating?.let {
                    stringResource(R.string.detail_rating_value, it.value)
                } ?: stringResource(R.string.detail_rating_unrated)
                Text(currentDescription)
                val selectedDescription = stringResource(R.string.detail_rating_slider, state.selectedValue)
                Slider(
                    value = state.selectedValue.toFloat(),
                    onValueChange = { onSelect(it.roundToInt()) },
                    valueRange = PersonalRating.MIN_VALUE.toFloat()..PersonalRating.MAX_VALUE.toFloat(),
                    steps = PersonalRating.MAX_VALUE - PersonalRating.MIN_VALUE - 1,
                    enabled = !state.updating,
                    modifier = Modifier.fillMaxWidth().semantics {
                        stateDescription = selectedDescription
                    }
                )
                Text(stringResource(R.string.detail_rating_selected_value, state.selectedValue))
                state.error?.let { error ->
                    Text(
                        text = stringResource(error.toUiError().messageRes),
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = onDismissError) {
                        Text(stringResource(R.string.action_dismiss))
                    }
                }
                Button(onClick = onSave, enabled = !state.updating, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(
                            if (state.updating) R.string.detail_rating_updating else R.string.detail_rating_set
                        )
                    )
                }
                if (state.rating != null) {
                    TextButton(
                        onClick = onRemove,
                        enabled = !state.updating,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.detail_rating_remove))
                    }
                }
            }
        }
    }
}
