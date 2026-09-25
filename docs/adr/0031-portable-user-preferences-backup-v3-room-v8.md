# ADR 0031: Portable user preferences, Backup v3, and Room v8

Status: Accepted
Date: 2026-09-24

Supersedes the current-format and preference-portability decisions in ADR 0017; prior v1/v2 files remain supported.

## Context

The backup reader rejects documents larger than 50 MiB and caps media, seasons, and episodes, while the writer could previously return a larger in-memory document. TV Time also accepted episode counts larger than the backup reader. In addition, users' theme, language, spoiler visibility, and six collection display modes were persisted in Preferences DataStore and omitted from backups, despite the product requirement for complete portable preferences. Writing those settings to DataStore during restore would sit outside the existing Room transaction and could leave a partially restored state.

## Decision

- Keep the restore byte ceiling at 50 MiB and use shared current-format portable-record limits of 50,000 media, 100,000 seasons, and 100,000 episodes. V3 encoding and parsing use these limits. V1/v2 readers retain their historical maximum of 500,000 episodes and episode-progress records, still subject to the byte ceiling, so valid legacy files are not invalidated. The backup encoder checks v3 counts before writing and bounds UTF-8 output; if the result exceeds the reader's byte or record limits, export fails explicitly before backup bytes are written or sharing begins. TV Time applies the same aggregate media, season, and episode record limits as v3.
- Backup v3 adds theme (`SYSTEM_DEFAULT`, `LIGHT`, `DARK`), language (`ENGLISH`, `ITALIAN`), spoiler visibility, and list/grid mode for each of the six watched, watch-later, and favorite movie/series collections. Existing notification lead time and category preferences remain portable. V1 and v2 remain importable and default new fields to system theme, English, visible spoilers, and list mode.
- Android permissions, notification enablement, credentials, tokens, and other device/runtime state remain device-specific and outside JSON.
- Move portable appearance, spoiler, and collection display settings into the existing `portable_preferences` Room row. Room v8 adds their columns and a separate legacy-settings bridge marker. On the first read or write, the bridge copies old DataStore values once in a Room transaction. Restore writes all portable settings and sets the marker in its existing replace transaction; subsequent bridges cannot reapply stale values. Notification enablement stays in DataStore.
- The Room transaction is the atomicity boundary for portable backup restore. No DataStore setting is written as part of restore; device-specific notification enablement is intentionally unaffected.

## Consequences

- Backups remain bounded and every emitted v3 backup respects the same configured byte and record ceilings as v3 restore; an oversized export reports an explicit size-limit failure. V1/v2 import preserves the old 500,000 episode limit for compatibility.
- V3 preserves these four user setting groups across device restore while legacy v1/v2 documents restore with deterministic defaults.
- Appearance, spoiler, and collection display settings now follow the Room source of truth. The one-time bridge is deliberately retained so existing DataStore values survive upgrade; its Room marker also protects settings restored before a settings screen has read them.
- Room 7 -> 8 is additive and non-destructive. Restore remains one Room transaction for all portable data; Android permission and notification enablement are outside that portable transaction by design.

## Verification

Codec coverage round-trips v3 preferences and verifies v1/v2 defaults, accepts legacy v1/v2 episode counts above the v3 ceiling, checks current season/episode counts at their ceilings, round-trips a document near the byte ceiling, and confirms over-limit output rejection. Room migration tests cover 7 -> 8 defaults. Backup store tests cover first-export legacy bridging and ensure a restore cannot be overwritten by old DataStore values.
