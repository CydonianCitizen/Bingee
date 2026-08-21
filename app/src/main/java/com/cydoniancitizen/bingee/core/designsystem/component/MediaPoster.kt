package com.cydoniancitizen.bingee.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.cydoniancitizen.bingee.R

/**
 * Poster artwork for a title.
 *
 * [contentDescription] follows the Compose image convention: it defaults to a description derived
 * from [title], and `null` marks the artwork decorative. Pass `null` wherever a clickable parent
 * already owns the combined description of the media item, because a merged semantics node
 * concatenates the child description and TalkBack would otherwise announce the title twice.
 */
@Composable
fun MediaPoster(
    title: String,
    posterUrl: String?,
    modifier: Modifier = Modifier,
    width: Dp = 96.dp,
    height: Dp = 144.dp,
    contentDescription: String? = stringResource(
        if (posterUrl == null) R.string.poster_missing else R.string.poster_description,
        title
    )
) {
    val posterModifier = modifier
        .width(width)
        .height(height)
        .clip(MaterialTheme.shapes.medium)
    val placeholder = painterResource(R.drawable.poster_placeholder)
    if (posterUrl == null) {
        Image(
            painter = placeholder,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = posterModifier
        )
    } else {
        AsyncImage(
            model = posterUrl,
            contentDescription = contentDescription,
            placeholder = placeholder,
            error = placeholder,
            fallback = placeholder,
            contentScale = ContentScale.Crop,
            modifier = posterModifier
        )
    }
}
