# ADR 0011: Use Room v1 for provider-aware local library persistence

- Status: Accepted
- Date: 2026-08-01

## Context

Milestone 4 needs the first durable user-owned media state. Search summaries must become locally renderable library entries without coupling domain or UI code to Room, TMDB DTOs, credentials, or network availability. One local work may gain references from more than one provider in later milestones, while provider IDs are not global identities.

Official Android guidance checked on 2026-08-01:

- [Room setup](https://developer.android.com/training/data-storage/room)
- [Asynchronous DAO queries](https://developer.android.com/training/data-storage/room/async-queries)
- [Defining Room entities](https://developer.android.com/training/data-storage/room/defining-data)
- [Room transactions and relationships](https://developer.android.com/training/data-storage/room/relationships/nested)
- [Migrations and schema export](https://developer.android.com/training/data-storage/room/migrating-db-versions)
- [Database testing](https://developer.android.com/training/data-storage/room/testing-db)

The guidance uses KSP for Kotlin projects, observable `Flow` queries and suspending one-shot operations, `@Transaction` for multi-query consistency, exported version-controlled schemas for migration verification, and device-side in-memory Room databases for representative DAO tests. Destructive migration fallback permanently deletes user data and is unsuitable for Bingee.

## Decision

Use one application-scoped Room database named `bingee.db`, version 1. Keep the exported schema under `app/schemas/` and verify in CI that generation does not leave an uncommitted schema diff. Do not configure main-thread access, callbacks, or destructive migration fallback.

Use a Room-generated numeric `Long` primary key as the internal local media ID. It is compact, stable for the database lifetime, and generated atomically by SQLite. It remains an infrastructure detail: repository contracts, ViewModels, composables, routes, logs, and user-visible data continue to identify media with `ExternalMediaRef(source, externalId)`.

Version 1 has exactly three tables:

- `media_entries` owns canonical list-level title, type, optional original title, overview, resolved poster URL, optional release date, creation time, and metadata-update time;
- `external_refs` maps the local ID to a unique `(source, external_id)` identity and permits multiple provider references for one local media row;
- `library_entries` owns only active membership and its first-added time.

Persist enum names, never ordinals. Persist `LocalDate` as ISO-8601 date text and `Instant` as ISO-8601 UTC text. Unknown enum names or malformed temporal values fail conversion and become a safe structured persistence error; they are not silently reinterpreted. Entity and domain invariants reject blank titles and provider IDs.

Adding a search result is one Room transaction. It finds by provider-aware reference, inserts a new local media row and reference when absent, or updates only list metadata when present. Existing `created_at` and membership `added_at` remain unchanged; `metadata_updated_at` advances. `ABORT` protects parent/reference identity conflicts, `UPDATE` refreshes the known row, and `IGNORE` is used only for idempotent membership insertion. Broad `REPLACE` is forbidden because it can delete and recreate parent rows.

Removing uses one atomic SQL delete against `library_entries`. Canonical `media_entries` metadata and `external_refs` remain as a metadata cache. No watched, progress, completion, rating, detail, season, episode, or release-event state exists in this schema. Cache pruning is deferred.

## Consequences

- Search membership and Library content observe Room through repository `Flow`s and update across screens without network work.
- Library remains readable without a TMDB credential; no token, query, DTO JSON, provider response, or exception body enters the schema.
- Repeated add/remove actions are idempotent and provider ID collisions across sources remain separate.
- A future schema change requires a database version increment, non-destructive migration, refreshed exported schema, and migration tests. Version 1 has no prior migration path to test.
- Future permanent cache deletion must explicitly account for foreign keys and personal-history retention; Milestone 4 does not expose it.
