package com.cydoniancitizen.bingee.feature.widget

import android.content.Context
import android.graphics.Bitmap
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.cydoniancitizen.bingee.core.model.ContinueWatchingItem
import com.cydoniancitizen.bingee.core.model.ReleaseDateCategory
import com.cydoniancitizen.bingee.core.model.ReleaseEvent
import com.cydoniancitizen.bingee.core.model.groupReleaseEvents
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.settings.AppTheme
import com.cydoniancitizen.bingee.data.settings.AppearancePreferences
import com.cydoniancitizen.bingee.domain.calendar.CalendarDateSource
import com.cydoniancitizen.bingee.domain.repository.LibraryRepository
import com.cydoniancitizen.bingee.domain.repository.ReleaseCalendarRepository
import com.cydoniancitizen.bingee.domain.repository.WatchProgressRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.first

internal const val WIDGET_UPCOMING_LIMIT = 2

/** What both widgets draw: the series to continue and the next releases, read from Room only. */
internal data class WidgetSnapshot(
    val watching: ContinueWatchingItem?,
    val upcoming: List<ReleaseEvent>,
    val today: LocalDate,
    /** The in-app theme choice, which widgets follow because the launcher never sees the app's night mode. */
    val theme: AppTheme
)

/** Today's and later releases in calendar order; past ones belong to the app's "recent" section. */
internal fun selectUpcoming(events: List<ReleaseEvent>, today: LocalDate): List<ReleaseEvent> =
    groupReleaseEvents(events, today)
        .filter { it.category != ReleaseDateCategory.RECENT }
        .flatMap { it.events }
        .take(WIDGET_UPCOMING_LIMIT)

internal fun widgetDayLabel(
    date: LocalDate,
    today: LocalDate,
    todayLabel: String,
    tomorrowLabel: String,
    locale: Locale
): String = when (date) {
    today -> todayLabel
    today.plusDays(1) -> tomorrowLabel
    else -> date.format(DateTimeFormatter.ofPattern("EEE d MMM", locale))
}

/** Widgets are not Hilt entry points themselves, so they reach the app graph through this. */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface WidgetEntryPoint {
    fun libraryRepository(): LibraryRepository
    fun releaseCalendarRepository(): ReleaseCalendarRepository
    fun watchProgressRepository(): WatchProgressRepository
    fun calendarDateSource(): CalendarDateSource
    fun appearancePreferences(): AppearancePreferences
}

internal fun Context.widgetEntryPoint(): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java)

internal suspend fun loadWidgetSnapshot(context: Context): WidgetSnapshot {
    val entryPoint = context.widgetEntryPoint()
    val today = entryPoint.calendarDateSource().currentDate()
    val watching = entryPoint.libraryRepository().observeContinueWatching().first()
    val events = entryPoint.releaseCalendarRepository().observeEvents(today).first()
    return WidgetSnapshot(
        watching = (watching as? AppResult.Success)?.value?.firstOrNull(),
        upcoming = selectUpcoming((events as? AppResult.Success)?.value.orEmpty(), today),
        today = today,
        theme = entryPoint.appearancePreferences().observeTheme().first()
    )
}

/**
 * Widgets draw through RemoteViews, which cannot take a hardware bitmap or load a URL, so the poster is
 * fetched up front through the app's image loader and its disk cache.
 */
internal suspend fun loadWidgetPoster(context: Context, url: String): Bitmap? {
    val request = ImageRequest.Builder(context)
        .data(url)
        .size(POSTER_WIDTH_PX, POSTER_HEIGHT_PX)
        .allowHardware(false)
        .build()
    return (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
}

private const val POSTER_WIDTH_PX = 300
private const val POSTER_HEIGHT_PX = 450
