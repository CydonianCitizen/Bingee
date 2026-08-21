# Bingee Roadmap

This document describes current delivery and explicitly deferred work for Bingee `v1.1.0`.

## Delivered in `v1.1.0`

- English and Italian localization with active `MissingTranslation` lint coverage.
- Home, Search, Your Bingee, Continue Watching, Notification Center, and settings subpages.
- The Your Bingee redesign of the personal top-level destination: actionable Watching, collection shortcuts with counts, Favorites, and a personal statistics preview. The destination keeps its internal `profile` route.
- Statistics 2.0: taste radar, All/Movies/Series genre scope, full genre ranking, exact viewing analytics, a monthly histogram for a selected year, and a personal ratings histogram with selected-result shelves.
- Branding and system-UI work: the current Bingee light/dark theme, edge-to-edge shell, and system-bar icon appearance following the active theme.
- Release-build optimization is configured in `app/build.gradle.kts` (`release { optimization { enable = true } }`), so R8 is no longer a separate pending batch.
- Room v4 local database, local release calendar, local notifications, Backup v1, TV Time additive import, and manual GitHub update checks.

## Deferred

- The Details screen redesign remains future UX work. Canonical serial state and a low-risk Details Abandoned control ship in v1.1.0.
- `La tua storia` / History, a year recap, and a later viewing-mode split remain planned rather than implemented.
- FTS, cache eviction infrastructure, generalized job-management abstractions, and other P3-7 optimizations remain measurement-driven work.
- Accounts, cloud sync, social features, streaming availability, recommendations, Jikan, and cross-provider deduplication remain out of scope.
