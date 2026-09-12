package com.cydoniancitizen.bingee.feature.details

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.core.designsystem.component.MediaPoster
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeDimensions
import com.cydoniancitizen.bingee.core.model.Genre
import com.cydoniancitizen.bingee.core.model.MediaDetails
import com.cydoniancitizen.bingee.core.model.MediaType

private val BackdropHeight = 240.dp
private val PosterWidth = 112.dp
private val PosterHeight = 168.dp
private val PosterTopOffset = 156.dp

/**
 * How much of the poster sits over the backdrop, kept as an absolute value so the overlap is the
 * same on every screen width and at every font scale. A proportional overlap drifts with the
 * display and stops lining up with the gradient that hides the backdrop's bottom edge.
 */
private val PosterBackdropOverlap = BackdropHeight - PosterTopOffset

/** Dark band over the artwork, sized for the top app bar's icons rather than for the hero. */
private val TopScrimHeight = 68.dp

/**
 * Opacity of that band. The top app bar's icons are white on every backdrop, so this is the only
 * thing standing between them and a bright one. Black at this alpha over pure white — the worst
 * case, and the one the placeholder backdrop produces in the light theme — resolves to sRGB 140,
 * which holds the 3:1 contrast WCAG asks of an icon. Lowering it below 0.42 breaks that floor and
 * the back, favourite and refresh icons start disappearing into pale artwork; raising it veils more
 * of the backdrop's top edge, which is the part of the image the hero exists to show.
 */
private val TopScrimAlpha = 0.45f

/**
 * How far down the band holds [TopScrimAlpha] before it starts fading out. The app bar's icon
 * glyphs measure out between roughly 21 dp and 46 dp from the top, and a band that starts fading at
 * 0 dp is already down to about half its alpha by the time it reaches them — which is how the icons
 * ended up at 1.80:1 over a pale backdrop despite the band nominally being dark enough. Lowering
 * this pulls the fade back up into the icons and the contrast floor goes with it; raising it past
 * [TopScrimHeight] leaves the band with no room to fade and it ends on a visible edge.
 */
private val TopScrimHoldHeight = 48.dp

/** Distance the scrim ramps up over before it reaches the text, so it has no visible edge. */
private val FadeRunway = 24.dp

/** The fade always keeps this much of the backdrop to finish in, so the artwork never ends on a cut. */
private val FadeTail = 60.dp

/** How much of the artwork the scrim has covered by the time the text column starts. */
private val TextCoverage = 0.85f

@Composable
internal fun DetailHero(details: MediaDetails, modifier: Modifier = Modifier) {
    val background = MaterialTheme.colorScheme.background
    val density = LocalDensity.current
    // The hero is as tall as its own content: past roughly fontScale 1.5 the text column outgrows
    // the poster, and a fixed height would clip the title rather than let the hero grow.
    var textHeight by remember { mutableStateOf(PosterHeight) }
    val heroHeight = PosterTopOffset + maxOf(PosterHeight, textHeight)
    // Anchored to the poster instead of to the hero, so the overlap survives that growth.
    val backdropHeight = heroHeight - PosterHeight + PosterBackdropOverlap
    Box(modifier = modifier.fillMaxWidth().height(heroHeight)) {
        Box(modifier = Modifier.fillMaxWidth().height(backdropHeight)) {
            Backdrop(details)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(heroScrim(background, backdropHeight, heroHeight - textHeight))
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = BingeeDimensions.screenPadding),
            horizontalArrangement = Arrangement.spacedBy(BingeeDimensions.contentSpacing),
            verticalAlignment = Alignment.Bottom
        ) {
            MediaPoster(
                title = details.title,
                posterUrl = details.posterUrl,
                modifier = Modifier.shadow(8.dp, MaterialTheme.shapes.medium),
                width = PosterWidth,
                height = PosterHeight
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .onSizeChanged { textHeight = with(density) { it.height.toDp() } },
                verticalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)
            ) {
                Text(
                    text = details.title,
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                details.originalTitle?.takeIf { it != details.title }?.let {
                    Text(
                        text = stringResource(R.string.detail_original_title, it),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = metaLine(details),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Two jobs in one pass: a dark band so the transparent top app bar's icons stay legible over bright
 * artwork, then a fade into the page background so the backdrop has no visible cut line where it
 * ends. The fade is sized from [textTop] — the measured top of the hero's text column — and is
 * already [TextCoverage] of the way to the background by the time it gets there, so the title sits
 * on background rather than on raw artwork at every font scale.
 *
 * Three constants tune the fade, and each one trades visible artwork against the title's contrast.
 * The band above it is tuned separately by [TopScrimAlpha], against the app bar icons instead.
 * Raising [TextCoverage] hides more of the image behind the text and keeps the title safe over a
 * bright backdrop. Raising [FadeRunway] starts the fade further up the image, so the transition is
 * gentler but the artwork is veiled earlier. Raising [FadeTail] pulls the whole fade up whenever
 * the text starts low on the backdrop, buying room to reach the page background before the
 * backdrop's bottom edge — again at the cost of artwork. Lowering any of them shows more artwork:
 * [TextCoverage] and [FadeTail] until the title lands on bare artwork, [FadeRunway] until the fade
 * begins as a visible edge. They were tuned against captures at fontScale 1.0, 1.5 and 2.0, with
 * the 1.0 frame — the one nearly everyone sees — held pixel-identical to an unscrimmed backdrop
 * above the fade.
 */
private fun heroScrim(background: Color, backdropHeight: Dp, textTop: Dp): Brush {
    val scrimEnd = (TopScrimHeight / backdropHeight).coerceIn(0f, 1f)
    val scrimHold = (TopScrimHoldHeight / backdropHeight).coerceIn(0f, scrimEnd)
    // Anchored on the text, pulled earlier only when the text starts so low that the fade would
    // have no room left to reach the page background before the backdrop's own bottom edge.
    val anchor = minOf(textTop, backdropHeight - FadeTail)
    val covered = (anchor / backdropHeight).coerceIn(scrimEnd, 1f)
    val rampStart = ((anchor - FadeRunway) / backdropHeight).coerceIn(scrimEnd, covered)
    return Brush.verticalGradient(
        0f to Color.Black.copy(alpha = TopScrimAlpha),
        scrimHold to Color.Black.copy(alpha = TopScrimAlpha),
        scrimEnd to Color.Transparent,
        rampStart to background.copy(alpha = 0f),
        covered to background.copy(alpha = TextCoverage),
        1f to background
    )
}

/**
 * Type, year and either runtime or season count, joined into one line. Facts the provider did not
 * supply are dropped rather than rendered as placeholders, so a sparse entry produces a shorter
 * line instead of a row of dashes.
 */
@Composable
private fun metaLine(details: MediaDetails): String {
    val type = stringResource(
        if (details.mediaType == MediaType.MOVIE) R.string.library_type_movie else R.string.library_type_tv
    )
    val year = details.releaseDate?.year?.toString()
    val extent = if (details.mediaType == MediaType.MOVIE) {
        details.runtime?.let { stringResource(R.string.detail_minutes, it.toMinutes()) }
    } else {
        details.numberOfSeasons?.takeIf { it > 0 }?.let {
            pluralStringResource(R.plurals.detail_seasons_count, it, it)
        }
    }
    return listOfNotNull(type, year, extent).joinToString(" · ")
}

@Composable
private fun Backdrop(details: MediaDetails) {
    val placeholder = painterResource(R.drawable.poster_placeholder)
    val modifier = Modifier.fillMaxSize()
    if (details.backdropUrl == null) {
        Image(
            painter = placeholder,
            contentDescription = stringResource(R.string.detail_backdrop_missing, details.title),
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        AsyncImage(
            model = details.backdropUrl,
            contentDescription = stringResource(R.string.detail_backdrop, details.title),
            placeholder = placeholder,
            error = placeholder,
            fallback = placeholder,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    }
}

/**
 * Production status and genres as labels built on [Surface].
 *
 * Deliberately not `AssistChip`/`SuggestionChip`: neither one is actionable here, and a chip that
 * announces itself as a button and then does nothing is a worse affordance than a plain label.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DetailChips(statusLabel: String, genres: List<Genre>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing),
        verticalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)
    ) {
        DetailChip(
            label = statusLabel,
            container = MaterialTheme.colorScheme.primaryContainer,
            content = MaterialTheme.colorScheme.onPrimaryContainer
        )
        genres.forEach { genre ->
            DetailChip(
                label = genre.name,
                container = MaterialTheme.colorScheme.surfaceContainerHigh,
                content = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DetailChip(label: String, container: Color, content: Color) {
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(50)) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge
        )
    }
}
