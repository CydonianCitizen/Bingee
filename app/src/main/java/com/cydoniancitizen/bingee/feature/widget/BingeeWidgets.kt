package com.cydoniancitizen.bingee.feature.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.cydoniancitizen.bingee.MainActivity
import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.core.designsystem.theme.DarkColorScheme
import com.cydoniancitizen.bingee.core.designsystem.theme.LightColorScheme
import com.cydoniancitizen.bingee.core.model.ContinueWatchingItem
import com.cydoniancitizen.bingee.core.model.EpisodePosition
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.ReleaseEvent
import com.cydoniancitizen.bingee.data.notification.NotificationDetailIntent
import com.cydoniancitizen.bingee.data.notification.NotificationNavigationTarget
import com.cydoniancitizen.bingee.data.settings.AppTheme
import com.cydoniancitizen.bingee.data.settings.AppearancePreferences
import com.cydoniancitizen.bingee.domain.calendar.CalendarDateSource
import com.cydoniancitizen.bingee.domain.repository.LibraryRepository
import com.cydoniancitizen.bingee.domain.repository.ReleaseCalendarRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest

/** "System default" keeps both palettes so the launcher picks by the phone's mode; a fixed choice pins one. */
private fun widgetColors(theme: AppTheme) = when (theme) {
    AppTheme.SYSTEM_DEFAULT -> ColorProviders(light = LightColorScheme, dark = DarkColorScheme)
    AppTheme.LIGHT -> ColorProviders(scheme = LightColorScheme)
    AppTheme.DARK -> ColorProviders(scheme = DarkColorScheme)
}

/** Same fixed disc as the Home poster control: the artwork under it can be any colour. */
private val CheckScrim = ColorProvider(Color.Black.copy(alpha = 0.6f))
private val CheckTint = ColorProvider(Color.White)

/** Small widget: the poster of the series being watched, with a button for its next episode. */
internal class ContinueWatchingWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = loadWidgetSnapshot(context)
        val poster = snapshot.watching?.posterUrl?.let { loadWidgetPoster(context, it) }
        provideContent {
            GlanceTheme(colors = widgetColors(snapshot.theme)) { PosterContent(snapshot.watching, poster) }
        }
    }
}

/** Medium widget: the series poster with its next-episode button, beside the next releases. */
internal class UpNextWidget : GlanceAppWidget() {
    // Exact, so the layout knows how tall the launcher made it and drops a release instead of clipping.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = loadWidgetSnapshot(context)
        val seriesPoster = snapshot.watching?.posterUrl?.let { loadWidgetPoster(context, it) }
        val releasePosters = snapshot.upcoming.map { event -> event.posterUrl?.let { loadWidgetPoster(context, it) } }
        provideContent {
            GlanceTheme(colors = widgetColors(snapshot.theme)) { UpNextContent(snapshot, seriesPoster, releasePosters) }
        }
    }
}

class ContinueWatchingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ContinueWatchingWidget()
}

class UpNextWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UpNextWidget()
}

internal suspend fun updateBingeeWidgets(context: Context) {
    ContinueWatchingWidget().updateAll(context)
    UpNextWidget().updateAll(context)
}

/**
 * Re-renders the widgets whenever what they show changes, whichever screen, worker or restore wrote it,
 * when the date rolls over, and when the in-app theme changes. Widgets cannot observe these themselves:
 * they only redraw when asked.
 */
@Singleton
internal class BingeeWidgetUpdater @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val libraryRepository: LibraryRepository,
    private val calendarRepository: ReleaseCalendarRepository,
    private val dateSource: CalendarDateSource,
    private val appearancePreferences: AppearancePreferences
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun keepWidgetsCurrent() {
        combine(
            libraryRepository.observeContinueWatching(),
            dateSource.observeDate().flatMapLatest { today -> calendarRepository.observeEvents(today) },
            appearancePreferences.observeTheme()
        ) { watching, events, theme -> Triple(watching, events, theme) }
            .distinctUntilChanged()
            // Widgets render their own state when placed or when the process starts for them.
            .drop(1)
            .collect { updateBingeeWidgets(context) }
    }
}

internal class MarkNextEpisodeAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val source = parameters[EpisodeSourceKey]?.let { name -> MediaSource.entries.firstOrNull { it.name == name } }
        val externalId = parameters[EpisodeIdKey]
        if (source == null || externalId == null) return
        // ponytail: a failed write leaves the widget unchanged with no message; widgets cannot show a
        // snackbar, so surface failures through a notification if they turn out to matter.
        context.widgetEntryPoint().watchProgressRepository().markEpisodeWatched(ExternalMediaRef(source, externalId))
        updateBingeeWidgets(context)
    }

    companion object {
        val EpisodeSourceKey = ActionParameters.Key<String>("episode_source")
        val EpisodeIdKey = ActionParameters.Key<String>("episode_external_id")
    }
}

private fun markNextEpisode(ref: ExternalMediaRef): Action = actionRunCallback<MarkNextEpisodeAction>(
    actionParametersOf(
        MarkNextEpisodeAction.EpisodeSourceKey to ref.source.name,
        MarkNextEpisodeAction.EpisodeIdKey to ref.externalId
    )
)

/** Opens the title through the same intent notifications use, or the app when it has no TMDB identity. */
private fun openDetails(context: Context, ref: ExternalMediaRef, mediaType: MediaType): Action {
    val tmdbId = ref.externalId.toLongOrNull()?.takeIf { ref.source == MediaSource.TMDB && it > 0 }
    return if (tmdbId != null) {
        actionStartActivity(NotificationDetailIntent.intent(context, NotificationNavigationTarget(mediaType, tmdbId)))
    } else {
        actionStartActivity<MainActivity>()
    }
}

@Composable
private fun PosterContent(item: ContinueWatchingItem?, poster: Bitmap?) {
    val context = LocalContext.current
    val base = GlanceModifier.fillMaxSize().cornerRadius(16.dp).background(GlanceTheme.colors.surfaceVariant)
    if (item == null) {
        Box(
            modifier = base.padding(12.dp).clickable(actionStartActivity<MainActivity>()),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = context.getString(R.string.profile_watching_empty_title),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, textAlign = TextAlign.Center)
            )
        }
        return
    }
    Box(modifier = base.clickable(openDetails(context, item.mediaRef, item.mediaType))) {
        if (poster != null) {
            Image(
                provider = ImageProvider(poster),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier.fillMaxSize()
            )
        } else {
            Text(
                text = item.title,
                modifier = GlanceModifier.padding(12.dp),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontWeight = FontWeight.Bold),
                maxLines = 3
            )
        }
        val next = item.nextEpisode
        val nextRef = item.nextEpisodeRef
        if (next != null && nextRef != null) {
            Box(modifier = GlanceModifier.fillMaxSize().padding(4.dp), contentAlignment = Alignment.BottomEnd) {
                CheckButton(next, nextRef)
            }
        }
    }
}

@Composable
private fun UpNextContent(snapshot: WidgetSnapshot, seriesPoster: Bitmap?, releasePosters: List<Bitmap?>) {
    val context = LocalContext.current
    val height = LocalSize.current.height
    // The series poster shrinks with a short widget rather than overflowing it; one release fits where two
    // thumbnails would be clipped.
    val seriesPosterHeight = (height - WIDGET_PADDING * 2 - SECTION_LABEL_HEIGHT)
        .coerceIn(MIN_SERIES_POSTER_HEIGHT, SERIES_POSTER_HEIGHT)
    val releaseCount = if (height < TWO_RELEASES_MIN_HEIGHT) 1 else WIDGET_UPCOMING_LIMIT
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(16.dp)
            .background(GlanceTheme.colors.surface)
            .padding(WIDGET_PADDING)
    ) {
        Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
            SectionLabel(context.getString(R.string.home_continue_watching))
            val item = snapshot.watching
            if (item == null) {
                SecondaryText(context.getString(R.string.profile_watching_empty_title), maxLines = 3)
            } else {
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    // Poster plus button, as in the small widget, so the control reads the same in both.
                    Box(
                        modifier = GlanceModifier
                            .size(width = seriesPosterHeight * 2 / 3, height = seriesPosterHeight)
                            .clickable(openDetails(context, item.mediaRef, item.mediaType)),
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        WidgetPoster(seriesPoster, GlanceModifier.fillMaxSize())
                        val next = item.nextEpisode
                        val nextRef = item.nextEpisodeRef
                        if (next != null && nextRef != null) {
                            CheckButton(next, nextRef, size = 32.dp)
                        }
                    }
                    Spacer(GlanceModifier.width(8.dp))
                    Column(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .clickable(openDetails(context, item.mediaRef, item.mediaType))
                    ) {
                        PrimaryText(item.title, maxLines = 3)
                        item.nextEpisode?.let {
                            SecondaryText(
                                context.getString(R.string.widget_next_episode, it.seasonNumber, it.episodeNumber),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
        Spacer(GlanceModifier.width(12.dp))
        Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
            SectionLabel(context.getString(R.string.widget_upcoming_title))
            if (snapshot.upcoming.isEmpty()) {
                SecondaryText(context.getString(R.string.widget_no_upcoming), maxLines = 3)
            }
            snapshot.upcoming.take(releaseCount).forEachIndexed { index, event ->
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .clickable(openDetails(context, event.mediaRef, event.mediaType)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WidgetPoster(
                        releasePosters.getOrNull(index),
                        GlanceModifier.size(width = RELEASE_POSTER_HEIGHT * 2 / 3, height = RELEASE_POSTER_HEIGHT)
                    )
                    Spacer(GlanceModifier.width(8.dp))
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        SecondaryText(
                            widgetDayLabel(
                                date = event.eventDate,
                                today = snapshot.today,
                                todayLabel = context.getString(R.string.widget_today),
                                tomorrowLabel = context.getString(R.string.widget_tomorrow),
                                locale = context.resources.configuration.locales[0]
                            ),
                            maxLines = 1
                        )
                        PrimaryText(event.widgetTitle(context), maxLines = 2)
                    }
                }
            }
        }
    }
}

/** A cropped poster, or a plain tile when it could not be loaded, so the layout keeps its shape offline. */
@Composable
private fun WidgetPoster(bitmap: Bitmap?, modifier: GlanceModifier) {
    // Decorative: the title beside it already names the item.
    if (bitmap != null) {
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.cornerRadius(8.dp)
        )
    } else {
        Box(modifier = modifier.cornerRadius(8.dp).background(GlanceTheme.colors.surfaceVariant)) {}
    }
}

private val WIDGET_PADDING = 12.dp
private val SECTION_LABEL_HEIGHT = 20.dp
private val SERIES_POSTER_HEIGHT = 108.dp
private val MIN_SERIES_POSTER_HEIGHT = 48.dp
private val RELEASE_POSTER_HEIGHT = 54.dp
private val TWO_RELEASES_MIN_HEIGHT = 170.dp

private fun ReleaseEvent.widgetTitle(context: Context): String {
    val season = seasonNumber
    val episode = episodeNumber
    return when {
        season != null && episode != null -> context.getString(R.string.widget_event_episode, title, season, episode)
        season != null -> context.getString(R.string.widget_event_season, title, season)
        else -> title
    }
}

@Composable
private fun CheckButton(next: EpisodePosition, ref: ExternalMediaRef, size: Dp = 40.dp) {
    val context = LocalContext.current
    // The touch target stays 48dp even where the visible disc is smaller, so the action that writes
    // progress is not missed for the poster tap beside it.
    Box(
        modifier = GlanceModifier.size(CHECK_TOUCH_TARGET).clickable(markNextEpisode(ref)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = GlanceModifier.size(size).cornerRadius(size / 2).background(CheckScrim),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_check),
                contentDescription = context.getString(
                    R.string.home_continue_watching_mark_watched,
                    next.seasonNumber,
                    next.episodeNumber
                ),
                colorFilter = ColorFilter.tint(CheckTint),
                modifier = GlanceModifier.size(size * 0.55f)
            )
        }
    }
}

private val CHECK_TOUCH_TARGET = 48.dp

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Medium),
        maxLines = 1
    )
    Spacer(GlanceModifier.height(4.dp))
}

@Composable
private fun PrimaryText(text: String, maxLines: Int) {
    Text(
        text = text,
        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium),
        maxLines = maxLines
    )
}

@Composable
private fun SecondaryText(text: String, maxLines: Int) {
    Text(
        text = text,
        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
        maxLines = maxLines
    )
}
