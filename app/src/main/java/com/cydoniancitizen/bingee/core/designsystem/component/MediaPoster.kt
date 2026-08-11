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
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.cydoniancitizen.bingee.R

@Composable
fun MediaPoster(title: String, posterUrl: String?, modifier: Modifier = Modifier) {
    val posterModifier = modifier
        .width(96.dp)
        .height(144.dp)
        .clip(MaterialTheme.shapes.medium)
    val placeholder = painterResource(R.drawable.poster_placeholder)
    if (posterUrl == null) {
        Image(
            painter = placeholder,
            contentDescription = stringResource(R.string.poster_missing, title),
            contentScale = ContentScale.Crop,
            modifier = posterModifier
        )
    } else {
        AsyncImage(
            model = posterUrl,
            contentDescription = stringResource(R.string.poster_description, title),
            placeholder = placeholder,
            error = placeholder,
            fallback = placeholder,
            contentScale = ContentScale.Crop,
            modifier = posterModifier
        )
    }
}
