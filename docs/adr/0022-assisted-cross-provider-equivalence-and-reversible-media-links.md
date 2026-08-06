# ADR 0022: Assisted cross-provider equivalence and reversible media links

- Status: Accepted
- Date: 2026-08-05

## Context

Bingee supports TMDB (Movies and TV Series) and Jikan (Anime). Certain works (such as animated feature films or single-season anime series) exist in both databases under distinct external identities (`MediaSource.TMDB` vs `MediaSource.JIKAN`). 

Users may wish to group corresponding provider representations in their local Library without losing provider-specific metadata, episode progress models, or personal ratings, and without making permanent or irreversible schema modifications.

## Decision

1. **Evidence Gate & User Control**: Cross-provider linking is strictly user-controlled and assisted (via optional "Possible Duplicate" suggestions in later parts). Automatic background merging is prohibited.
2. **Reversible Association Model**: A link creates a non-destructive relationship between existing local media entities (`MediaEntity`). No `media_entries` or `external_refs` rows are deleted, reparented, or altered upon linking or unlinking.
3. **Cardinality**: An active link group contains exactly two distinct media records. Arbitrary multi-member merging is prohibited.
4. **Identity Preservation**: `MediaSource` and external IDs remain the authoritative keys for external API calls and metadata refreshes. Stable group UUIDs (`MediaLinkGroupId`) are independent of Room auto-increment keys.
5. **Independent Personal State**:
   - **Progress**: TMDB episode progress (`watch_progress`) and Jikan entry progress (`anime_progress`) remain independent. Linking does not copy, combine, or erase progress records.
   - **Ratings**: Personal ratings (`media_ratings`) remain bound to individual local media IDs.
   - **Library Membership**: Both underlying records preserve their `library_entries` state.
6. **Metadata Provenance & Display**: A link group specifies a `preferred_presentation_media_id` used for consolidated card rendering in Library views. Unlinking instantly restores independent presentation for both records with zero data loss.
7. **Search Isolation**: Search remains provider-isolated (Movies, TV Series, Anime per ADR 0020). No unified mixed Search list is added.
8. **Persistence Model (Room v1 initial stable schema)**:
   - `media_link_groups` (`local_group_id` PK, `group_uuid` UNIQUE, `preferred_presentation_media_id` FK RESTRICT, `created_at`, `updated_at`)
   - `media_link_members` (`local_group_id` FK CASCADE, `local_media_id` FK RESTRICT UNIQUE, `added_at`)
   - `media_link_audit` (`audit_id` PK, `group_uuid`, `action`, `action_timestamp`, `origin`, preferred snapshot fields)
   - `media_link_audit_members` (`audit_id` FK CASCADE, `source`, `media_type`, `external_id` PK)
9. **Backup Replace Policy**: Restoring a backup clears active link groups and audit rows prior to replacing media rows, ensuring foreign-key integrity without dangling links.
10. **Backup Portability (Backup Schema v1)**: Exporter emits backup schema version 1 containing active `mediaLinkGroups` and privacy-safe `mediaLinkAudit` events, exported using provider-qualified external identities (`source` + `mediaType` + `externalId`) and stable group UUIDs without exposing Room auto-increment IDs. Importer accepts schema version 1.
11. **Audit Retention Policy**: Complete local link audit trail is preserved in backup v1 sorted deterministically by timestamp, group UUID, action, and member identity. Historical audit events for unlinked groups survive unlinking and are exported without requiring active media records.

## Consequences

- Domain and persistence layers can atomically create, observe, change preferred presentation, and unlink pair associations with zero personal state loss.
- Unlinking completely restores pre-linked state.
- Provider boundaries, error handling, rate limits, and isolated search dispatch remain clean and unpolluted.
- Candidate detector provides local-only, zero-network, unpersisted candidate evaluation based on shared IMDb identity, title variants, release year, and format compatibility, while excluding active link members.
- UI presentation and manual linking controls build upon this foundation.
- Backup v3 guarantees lossless portability and transactional restore of reversible media link groups and privacy-safe audit trail.

