# Bingee Roadmap

This document retains earlier delivery summaries. The [canonical development roadmap and checklist](../BINGEE_DEVELOPMENT_ROADMAP_AND_CHECKLIST.md) defines the active implementation order and release gates.

## Implemented in `v1.2.4`

- UI instrumentation uses resources and existing semantics for ordinary controls, with representative Italian execution and production Notification Center content.
- Optimized release fixes preserve the Glance episode callback, give changed-title detail intents their own navigation entry, and keep active widgets observing local changes.
- Home retains cached content and offers local Retry when calendar, membership or Continue Watching reads fail. Shared progress consumers stay subscribed through transient errors and receive recovery.
- Statistics derives radar sizing from the actual container, preserving full-width phone rendering.

See [v1.2.4 implementation notes](release-notes-1.2.4.md). Items #1–#6 are implemented; the hardware-efficiency audit (#7), final global audit and release gate remain open in the canonical checklist.

## Implemented in `v1.2.3`

- Date actions wrap rather than lose the clear-date label at narrow widths and larger fonts.
- Actual Favorite toggles use a consistent selected state; shortcuts and filters keep their distinct roles.
- Search and Home describe saved membership as Collection, preserving genuine Watch Later and other tracking states.
- Details artwork is decorative beside the visible title; notification thumbnails use the shared poster rounding.
- Statistics opens from both direct Collection launcher shortcuts, with the existing Back/Up path preserved.
- Populated Your Bingee Watching/Favorites shelves use saveable, typed provider keys, fixing a crash found by the final full Android suite.

See [v1.2.3 release notes](release-notes-1.2.3.md) for the changed behavior. The canonical checklist records verification and release readiness separately.

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

## Current data portability hardening

- Room v8 stores portable appearance, spoiler, and collection-view settings with the other restorable preferences; Backup v3 exports them, keeps v1/v2 imports compatible, and rejects documents that exceed the 50 MiB or v3 record ceilings. TV Time shares the current 50,000-media, 100,000-season, and 100,000-episode limits. Legacy v1/v2 episode reads retain their 500,000-record ceiling.

## Deferred

- A large (4×4) home screen widget remains to be designed.
- `La tua storia` / History, a year recap, and a later viewing-mode split remain planned rather than implemented.
- FTS, cache eviction infrastructure, generalized job-management abstractions, and other P3-7 optimizations remain measurement-driven work.
- Accounts, cloud sync, social features, streaming availability, recommendations, Jikan, and cross-provider deduplication remain out of scope.
