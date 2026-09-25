# ADR 0017: Versioned JSON backup and transactional restore

- Status: Accepted
- Date: 2026-08-04

## Context

Bingee must preserve user-owned viewing history across reinstall and provider availability without exporting credentials or internal storage details. Room schema evolution and a public backup contract have different compatibility needs.

## Decision

The public contract is UTF-8 JSON with `formatId = "bingee-backup"` and independent `schemaVersion = 1`. Dedicated backup models and explicit Gson JSON writing/reading define stable field names and ordering; Room entities and Retrofit DTOs are never serialized directly. Unknown additive fields are ignored, while missing, malformed, unsupported, duplicate, conflicting, or missing-reference records reject the complete document.

Portable data includes provider-qualified media metadata and all references for titles with Library membership, rating, movie progress, or episode progress; all cached seasons/episodes for included series; membership/timestamps; progress; ratings; and notification lead/category choices. Credential material, device permission/enablement state, notification channels/delivery ledger, WorkManager state, refresh/freshness data, internal IDs, raw provider responses, and UI state are excluded. Files are plaintext and bounded to 50 MiB with documented record/string limits.

MVP restore has one mode, `REPLACE_PORTABLE_DATA`. SAF reads the selected document once; complete parsing and semantic validation produce an in-memory immutable plan and concise preview. Explicit `Replace local data` confirmation is required. One Room transaction clears conflicting portable/derived tables, inserts new local IDs, restores portable records/preferences, rebuilds release events, and clears refresh/delivery state. Failure rolls back. Repeating a valid file is deterministic and does not duplicate records. No merge UI or best-effort import exists.

Room v7 adds singleton `portable_preferences` for lead days and movie/season/episode category choices plus an internal legacy-bridge marker. DataStore retains only device-local notification enablement. The idempotent bridge copies old lead/category values into Room in a transaction; interrupted attempts can safely retry. Restore updates Room preferences in the same restore transaction and never changes enablement or permission.

SAF uses `CreateDocument("application/json")` and `OpenDocument` without broad storage permissions. Optional sharing creates one private cache file, exposes only that directory through an exported=false `FileProvider`, grants read-only temporary URI permission, and cleans stale files at startup/next share. No URI or backup content is logged.

## Consequences

The format is portable and auditable, but future breaking public changes require explicit parser/version support. Detail-cache freshness and remote metadata must be rebuilt/refreshed after restore. Room migrations can evolve independently without forcing a backup schema change. Merge, encryption, cloud backup, and third-party import remain out of scope.
