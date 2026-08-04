# Bingee backup format v1

## Contract

Bingee backup files are UTF-8, human-readable JSON with:

- `formatId`: stable value `bingee-backup`;
- `schemaVersion`: integer `1`, independent from Room version;
- `exportedAt`: UTC ISO-8601 `Instant`;
- `data`: portable records grouped by responsibility.

The machine-readable contract is [bingee-backup-v1.schema.json](backup/bingee-backup-v1.schema.json). Unknown additive fields are ignored; missing, malformed, conflicting, or unsupported core fields reject the complete file.

`LocalDate` values use `YYYY-MM-DD`. Enums use names, never ordinals. Provider IDs are nonblank strings qualified by `source`; Milestone 10 accepts `TMDB` only.

## Portable data

`media` contains every active Library title, rated title, movie-progress title, or series with episode progress. It includes a deterministic primary reference, all external references, type, title, optional canonical overview/poster, and release/first-air date. Inactive metadata with no personal state is excluded.

`seasons` and `episodes` contain all cached records for included series, including season zero and unwatched episodes. Cache freshness, response bodies, and internal Room IDs are excluded.

`library` preserves membership and `addedAt`. `movieProgress` and `episodeProgress` preserve watched timestamps. Presence of a progress record is the watched source of truth. `ratings` preserves value, `ratedAt`, and `updatedAt`.

`preferences` contains only notification lead days and movie/season/episode category selections. Notification enablement, Android permission and channel state remain device-local.

Excluded: TMDB credential or encrypted ciphertext, Keystore aliases, device IDs, Room IDs/table shape, WorkManager state, notification-delivery ledger, refresh/freshness timestamps, raw provider responses/DTOs, search/UI state, and selected file URI.

## Ordering and duplicates

Arrays are sorted by provider-aware external identity, parent identity, season/episode number, then provider ID as applicable. Export does not depend on UI order, network, credential, or current time beyond `exportedAt`.

Duplicate identities inside one file are rejected. This includes media references, seasons, episodes, Library rows, progress rows, and ratings. One external reference cannot belong to two media records. Repeating a valid file is safe because restore replaces portable state.

Restore has one MVP mode: `REPLACE_PORTABLE_DATA`. It validates the whole file, presents a preview, then performs one Room transaction. Failure before commit leaves existing portable data unchanged. Restore regenerates local Room IDs, rebuilds release events, clears technical calendar/delivery state, and does not refresh remotely or post notifications.

Limits: 50 MiB input; 50,000 media; 100,000 seasons; 500,000 episodes; bounded metadata strings and URLs. No archives, paths, ZIP, encryption, merge, or partial-record import.

## Privacy

Backup JSON is plaintext and is not encrypted or password protected. It may contain the user's Library, viewing history, ratings, and selected notification categories. Store and share it only where appropriate. Invalid or unsupported files cannot partially modify the database.

Future breaking formats increment `schemaVersion` and require explicit parser support; Room migrations do not automatically change this contract.
