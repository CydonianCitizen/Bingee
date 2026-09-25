# 0026: Restore canonical genre statistics and portable genre metadata

- Status: Accepted
- Date: 2026-09-10

## Context

The working-tree rollback to Room v2 removed genre identity while retaining the reachable Your Bingee podium, Statistics radar, and genre ranking. TMDB still supplied canonical IDs, but cache writes discarded them, the DAO selected literal NULL identities, and the repository correctly rejected unidentified genres. Backup v1 omitted all genres and restore deleted the cache.

## Decision

Reinstate the additive Room 2 -> 3 migration and exact genre schema from ADR 0024: nullable `source` and `genre_id`, with their composite index. Preserve legacy names and order without inferring IDs from labels or title references. Keep the existing canonical aggregation and UI. The Room schema itself stays at v4 as shipped in 1.1.0, including ADR 0025 favorite chronology: 1.1.0 installs already hold a v4 database, and a lower declared version has no downgrade path.

Backup export advances to v2 and includes an ordered `genres` array on each portable media record. Each item contains `name` and a nullable identity pair `source`/`genreId`. Preserve unidentified legacy labels as well. Import validates identity pairing, positive IDs, supported provider, nonblank bounded labels, and at most 100 genres per title before mutation. Restore inserts genres with regenerated parent IDs inside the existing transaction, without creating `media_details` or claiming cache freshness.

V1 remains importable with absent genres interpreted as unavailable metadata. V2 requires the array, including an empty array where no genres exist. Old readers reject v2 instead of silently dropping genre metadata. No credentials, provider responses, or freshness data are added to backups.

## Compatibility and rollback

Existing database rows survive migration unchanged; unknown genre identity remains unknown until a successful normal Details refresh. The v3 and v4 schema identities are retained unchanged, so this decision needs no new Room version, and no destructive downgrade is introduced.

Rollback of application logic must retain the expanded Room schema and backup reader: installing a reader for an older database version over a newer one is unsupported. Keep a portable backup before deployment; do not delete data to permit downgrade. Repeated restore is transactional and retains semantic genre content while regenerating local IDs.

## Verification

Focused tests cover the Details cache mapper, Room/repository aggregation, localized duplicates, multiple titles, missing and legacy genres, migration preservation, backup codec/validation, and genre restore rollback. Existing domain, ViewModel, and UI tests remain consumers of the same canonical model.
