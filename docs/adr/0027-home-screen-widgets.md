# ADR 0027: Home screen widgets with Glance

- Status: Accepted
- Date: 2026-09-12

## Context

Users want to see what to watch next and mark an episode without opening the app. Android widgets are drawn by the launcher through RemoteViews, outside the app's Compose tree, Hilt ViewModels, and night mode, and they only redraw when the app asks them to.

## Decision

Ship two widgets built with Jetpack Glance (`androidx.glance:glance-appwidget` and `glance-material3`, Apache 2.0) in `feature/widget`:

- a 2×2 widget showing the poster of the most recent Continue Watching series, with a button that marks its next episode watched;
- a 4×2 widget pairing that series with the next two releases from the cached calendar.

Widgets read the existing repositories through a Hilt entry point and reuse the Continue Watching and calendar projections, so they add no Room schema, never call TMDB, and work offline. Posters load through the app's Coil image loader as software bitmaps. The button writes through `WatchProgressRepository.markEpisodeWatched`, the same path as Home. Tapping a poster or title opens Details through the existing notification detail intent.

One application-scoped observer combines Continue Watching, calendar events, the current date, and the in-app theme, and redraws both widgets when any of them changes, so every writer (screens, workers, restore, import) is covered without per-caller calls. An hourly `updatePeriodMillis` covers date changes while the process is not running.

Widgets follow the in-app theme preference: System default keeps both palettes and lets the launcher choose, Light and Dark pin one.

## Consequences

The launcher shows titles, next episodes, posters, and upcoming releases to anyone who can see the home screen; users control this by adding or removing the widgets. Widgets use the system font because RemoteViews cannot load custom fonts. A failed write from the widget leaves it unchanged without a message, because widgets cannot show a snackbar; this is marked for revisiting if it matters. A 4×4 widget is deferred.
