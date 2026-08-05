# Bingee backup format v2

Bingee exports UTF-8 JSON with `formatId: "bingee-backup"` and `schemaVersion: 2`. Import continues to accept version 1 unchanged.

Version 2 retains every v1 portable collection and adds:

- `animeDetails`: provider-specific metadata sufficient for an offline anime detail and Library card;
- `animeRelations`: navigable Jikan references only; relations never merge entries or personal state;
- `animeProgress`: one entry-level watched count, optional completion time and completion origin, and update time.

Anime identity is always `JIKAN + MAL/Jikan anime ID`. The same numeric ID under TMDB and Jikan is valid and identifies two different titles. Anime media use `mediaType: "ANIME"`; Jikan format remains a separate field.

The portable contract includes current cached metadata, membership, local title rating, progress, and relation labels. It excludes internal Room IDs, detail freshness, raw provider payloads, HTTP/rate-limit state, credentials, search queries, WorkManager state, notification permission/enablement, and delivery history.

Before mutation, import validates all references, provider/type pairs, positive Jikan IDs, unique provider-qualified identities, progress bounds, completion consistency, and relation provider boundaries. Restore regenerates local IDs and anime premiere events in one Room transaction, without network access or notification delivery. Any stage failure rolls back the entire replace.

Version 1 documents and fixtures remain accepted. A version 1 restore creates no anime detail, relation, or progress rows.

Schema: [bingee-backup-v2.schema.json](backup/bingee-backup-v2.schema.json).
