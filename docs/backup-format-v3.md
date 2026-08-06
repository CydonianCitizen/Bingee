# Bingee backup format v3

Bingee exports UTF-8 JSON with `formatId: "bingee-backup"` and `schemaVersion: 3`. Import continues to accept versions 1, 2, and 3.

Version 3 retains every v1 and v2 portable collection and adds:

- `mediaLinkGroups`: active reversible media-link associations preserving stable group UUIDs, exactly two provider-qualified member identities (`source`, `mediaType`, `externalId`), preferred presentation member identity, and creation/update timestamps;
- `mediaLinkAudit`: privacy-safe link audit events (`LINKED`, `UNLINKED`, `PREFERRED_PRESENTATION_CHANGED`) with timestamps, origin (`MANUAL_USER_ACTION`, `RESTORED_BACKUP`), exactly two member snapshots, and preferred presentation snapshots.

Media link members are exported with provider-qualified identities (`source` + `mediaType` + `externalId`). Active link group members are automatically included in the portable media set even if they lack Library membership, progress, or ratings. Historical unlinked audit events survive unlinking and are exported without requiring active media rows.

The portable contract excludes internal Room IDs, candidate classifications, candidate dismissal history, raw provider payloads, HTTP/rate-limit state, credentials, search queries, WorkManager state, notification enablement, and delivery history.

Before mutation, import validates format structure, schema version (1–3), duplicate UUIDs, two-member cardinality, preferred-member membership within the pair, single active group membership per media, identity resolution within the portable media set, and state history consistency. Restore regenerates local database IDs and active link structures in a single Room transaction. Any stage failure rolls back the entire restore operation.

Version 1 and version 2 documents and fixtures remain accepted. Restoring a version 1 or version 2 backup clears current link groups and link audit records prior to replacing media rows.

Schema: [bingee-backup-v3.schema.json](backup/bingee-backup-v3.schema.json).
