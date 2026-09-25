# ADR 0029: Restore-safe progress coverage, cache language, refresh rotation, and Room v6

- Status: Accepted
- Date: 2026-09-12

## Context

The 2026-09-12 codebase audit found three rules that read one piece of evidence as another:

- Series completeness required `seasons.episodes_fetched_at`, a freshness timestamp, as proof that every episode was stored. Restore never writes that timestamp (ADR 0017), so a series complete before export became incomplete offline, in Library and statistics alike.
- Background refresh chose its 20 titles by their last *successful* Details refresh. Titles that always fail (a TMDB 404, a removed entry) never advanced, so the same batch was picked on every run and the rest of the library went stale.
- Cache freshness depended only on age. After a language change, text cached in the previous language stayed fresh for up to 24 hours, and a reply requested before the change could be stored as fresh text for the new one.

## Decision

**Coverage is a data rule, not a freshness rule.** A regular season is covered when its stored episode rows match the episode count the provider declared and that count is positive. Episode rows only come from provider season data or a validated backup of it, so the match is the evidence, and a restored season keeps it offline. A declared zero can mean an unknown count, so an empty season still needs one season fetch. `episodes_fetched_at` keeps its freshness meaning and restore keeps writing null: no fetch is simulated. The Kotlin rule (`CachedSeason.hasSufficientEpisodeCoverage`) and the `LibraryDao`/`WatchProgressDao` SQL state the same rule. Backups need no new field, so v1 and v2 files restore the same completeness.

**Room v6** adds, through the non-destructive migration 5 -> 6:

- `background_refresh_attempts` (`local_media_id` primary key, cascading to `media_entries`; `attempted_at`). The planner claims its batch in one transaction: it selects the least recently attempted active titles, never-attempted first and then by the previous success-age order, and records the attempt for all of them. A failed, cancelled, or retried batch therefore moves behind the rest, and every title is reached in turn. `details_fetched_at` still advances only on a successful refresh. The table is technical state: it is neither exported nor restored, and a restore clears it with the media rows.
- Nullable `media_details.language` and `seasons.episodes_language`, holding the TMDB language tag the cached text was requested in. The TMDB clients report the tag they used, and the stores persist it with the rows. Text in another language than the current app language is stale however young it is, so it stays visible and is refreshed; null means unknown and ages normally. Existing rows migrate as unknown instead of being guessed.

## Consequences

A restored library shows the same complete series offline as before export, and statistics agree with Library because both read the same coverage rule. The residual gap is a regular season TMDB declares empty: after a restore it counts as covered only after its next season fetch.

Background refresh no longer starves titles behind permanent failures. A batch lost to a network outage is also rotated behind the others instead of being retried first; its titles return within one pass over the library. The 20-title batch bounds titles, not requests: a series still refreshes its details plus every relevant season, and after a restore every season is relevant once.

A language change marks cached details and episodes stale without deleting them; they refresh the next time they are opened or refreshed, and a reply in the old language stays stale. Rows cached before v6, and rows written by the TV Time import, carry an unknown language and keep age-only freshness until their next refresh. A Details screen already open when the language changes does not refresh again until it is reopened.

Rollback to a reader for Room v5 is unsupported, as for every earlier version; keep a portable backup before downgrading.
