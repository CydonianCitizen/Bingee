# 0024: Stable TMDB genre identity and Room v3

- Status: Accepted
- Date: 2026-08-16

## Context

TMDB localizes genre names. Persisted labels such as `Drama` and `Dramma` therefore cannot identify one genre reliably for future statistics.

## Decision

`Genre` carries nullable `MediaSource` and numeric genre ID beside its localized name. Successful TMDB Details mapping supplies `MediaSource.TMDB` and the positive TMDB genre ID. Details cache refresh replaces ordered genre rows atomically, so language changes update only display names.

Room v3 adds nullable `source` and `genre_id` columns to `media_genres`. Migration 2 -> 3 preserves legacy names and order without guessing identity or doing network work. A non-unique composite index on (`source`, `genre_id`) supports future provider-qualified aggregation without duplicating the primary-key index.

Backup remains `bingee-backup` v1 and excludes Details genre cache metadata.

## Consequences

Legacy cached genres remain readable with null identity until a successful Details refresh. Future statistics can measure metadata coverage and group only by provider-qualified ID rather than localized name.
