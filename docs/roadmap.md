# Bingee Roadmap

This document describes current delivery and explicitly deferred work for Bingee `v1.2.0`.

## Delivered in `v1.2.0`

- The Details screen rebuilt around a backdrop hero, with a top app bar that fades in over the artwork on scroll and one scrolling list for seasons and episodes.
- Continue Watching after the Home featured rows, showing the last watched and next episode and marking the next one watched in one tap, with undo.
- Two home screen widgets: a 2×2 poster of the series in progress with a next-episode button, and a 4×2 view that adds the next two releases. Both follow the in-app theme choice (ADR 0027).
- Oswald for headings and section titles, Inter for body text; the watchlist is marked with a bookmark throughout.
- A root surface that keeps screens outside a Scaffold, such as the startup check, readable in the dark theme.

## Delivered in `v1.1.0`

- English and Italian localization with active `MissingTranslation` lint coverage.
- Home, Search, Your Bingee, Continue Watching, Notification Center, and settings subpages.
- The Your Bingee redesign of the personal top-level destination: actionable Watching, collection shortcuts with counts, Favorites, and a personal statistics preview. The destination keeps its internal `profile` route.
- Statistics 2.0: exact viewing analytics, a monthly histogram for a selected year, and a personal ratings histogram with selected-result shelves. The taste radar, genre ranking, and profile genre podium use restored provider-qualified genre identity; Backup v2 preserves genres offline. Legacy names without IDs remain uncounted until a Details refresh.
- Branding and system-UI work: the current Bingee light/dark theme, edge-to-edge shell, and system-bar icon appearance following the active theme.
- Release-build optimization is configured in `app/build.gradle.kts` (`release { optimization { enable = true } }`), so R8 is no longer a separate pending batch.
- Room v5 local database, local release calendar, local notifications, Backup v2 (v1 import supported), TV Time additive import, and manual GitHub update checks.
- Canonical serial state and a low-risk Details Abandoned control.

## Deferred

- A large (4×4) home screen widget remains to be designed.
- `La tua storia` / History, a year recap, and a later viewing-mode split remain planned rather than implemented.
- FTS, cache eviction infrastructure, generalized job-management abstractions, and other P3-7 optimizations remain measurement-driven work.
- Accounts, cloud sync, social features, streaming availability, recommendations, Jikan, and cross-provider deduplication remain out of scope.
