# ADR 0028: Type-aware TMDB identity and Room v5

- Status: Accepted
- Date: 2026-09-12

## Context

TMDB numbers movies and TV series in separate sequences, so movie `1399` and series `1399` are unrelated titles. `external_refs` was keyed by (`source`, `external_id`) alone. Adding the second title of such a pair updated the first one's row: library membership, favorites, ratings, and Details cache of one title leaked into the other, and backups could not tell them apart.

Separately, movie runtime lived only in the `media_details` cache. A restore does not recreate that cache, so watch-time statistics lost every movie minute until each title was reopened online.

## Decision

A title's provider identity is (`source`, `media_type`, `external_id`). Room v5 adds `media_type` to the `external_refs` primary key. Migration 4 -> 5 rebuilds the table in place, copying each row's type from its `media_entries` row, so no row is dropped. Every DAO, repository, and ViewModel lookup by external ID now also passes the media type; operations that only make sense for one type (movie progress, seasons, abandoned series) pin it in SQL.

Room v5 also adds nullable `media_entries.runtime_minutes`. The migration seeds it from cached movie details; later Details refreshes and library adds keep it current. Statistics prefer the Details value and fall back to the stored one.

Backup format v2 gains two optional, additive fields: `runtimeMinutes` on movie records and `mediaType` on library and rating entries. Exports always write the type. An untyped entry, as in every v1 backup, must match exactly one media record; a reference to a shared ID without a type is rejected instead of guessed.

## Consequences

Movie and series pairs with one TMDB ID coexist with independent state. The migration runs once on update and keeps all data. Backups written before this change still import, because such libraries could never hold both titles of a pair. Readers that ignore unknown fields keep working; the schema version stays 2.
