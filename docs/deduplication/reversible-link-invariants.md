# Reversible Link Invariants & State Preservation Rules

## 1. Core Principle of Reversible Linking

> **A link in Bingee is a user-controlled, non-destructive association between separate, provider-qualified local media records.**

Linking **NEVER**:
- Deletes any `media_entries` or `external_refs` row.
- Rewrites or merges external identifiers (e.g. converting Jikan ID to TMDB ID).
- Overwrites, combines, or deletes watched progress.
- Overwrites, combines, or deletes personal title ratings.
- Removes library membership from any linked record.
- Synthesizes synthetic/hybrid provider metadata.

Unlinking **ALWAYS**:
- Instantly restores independent UI presentation for both records.
- Preserves 100% of progress, ratings, history, events, and library entries intact.

---

## 2. Domain & Data Invariants

### 2.1 Media Identity & Persistence
- Every provider record retains its local primary key (`local_media_id`) and provider identity (`MediaSource` + `external_id`).
- Link groups (`media_link_groups`) reference member records via foreign keys (`media_link_members`), but do not own or encapsulate media metadata or personal state.

### 2.2 Watch Progress Independence
- **TMDB Movies / TV Series**: Episode-level or movie-level watched state stored in `watch_progress` table.
- **Jikan Anime**: Entry-level episode count progress stored in `anime_progress` table.
- **Policy**:
  - Linking does **NOT** sync, copy, or convert watch progress between TMDB and Jikan.
  - Linked presentation UI may display both progress indicators side-by-side or show the progress of the user's preferred presentation provider.
  - Unlinking leaves all progress records untouched in their respective tables.

### 2.3 Rating Independence
- Personal ratings remain stored per `local_media_id` in `media_ratings`.
- Linking does **NOT** overwrite or average ratings.
- Linked UI presentation displays the rating associated with the preferred presentation provider (or displays both).

### 2.4 Library Membership Independence
- Library membership (`library_entries`) remains tracked per `local_media_id`.
- If Entry A is in Library and Entry B is not, linking them does **NOT** force Entry B into the Library or remove Entry A.
- If both are in Library, linked presentation displays a single consolidated Library card (using the preferred presentation provider), but unlinking restores both individual cards.

### 2.5 Metadata Provenance & Display Preference
- No composite metadata records are generated.
- The link group records a `preferred_presentation_media_id`.
- Title, poster, overview, and release dates displayed in main lists derive strictly from the preferred record.
- Detail screen provides access to view both original provider detail pages.

### 2.6 Release Events & Calendar
- Release events (`release_events`) for TMDB and Jikan remain separate and attributed to their source.
- Notifications deliver according to original rules and user preferences.

---

## 3. Link Audit Trail Requirements

Every link and unlink action creates an audit event in `media_link_audit`:

- `group_id`: Unique link group identifier.
- `action`: `LINKED`, `UNLINKED`, or `PREFERRED_PRESENTATION_CHANGED`.
- `timestamp`: UTC Instant of action.
- `member_snapshot`: Safe provider-qualified identities (`[TMDB:129, JIKAN:199]`).
- `origin`: `MANUAL_USER_ACTION` or `RESTORED_BACKUP`.

Audit log contains **NO** private user titles, search queries, progress values, or ratings.
