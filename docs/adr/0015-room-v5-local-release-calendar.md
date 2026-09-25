# ADR 0015: Room v5 local release calendar and manual refresh

## Status

Accepted

## Context

Milestone 8 turns Home into an offline-capable calendar for followed titles. Cached dates predate the feature, membership must control visibility without owning metadata, and a failed refresh must never erase usable local events or personal state.

## Decision

Room version 5 adds release_events and calendar_refresh_state. Event identity is the unique tuple of source, subject type, subject external ID, and event type. Subject type is media, season, or episode; event type is movie release, season premiere, or episode airing. Rows keep private local foreign keys for joins plus ISO LocalDate, projection time, and source-metadata update time. Display title, poster, numbering, watched state, rating, membership, formatted dates, relative labels, provider bodies, and credentials are not duplicated into events.

Migration 4-to-5 creates both structures and deterministically backfills every non-null cached movie release, season air date, and episode air date. Source metadata timestamps seed projection timestamps; no current time, network call, credential access, fabricated date, or successful-refresh timestamp is introduced. A repeatable local backfill reconciles later existing data and remains idempotent.

Metadata and corresponding projection updates share a Room transaction. Date changes update the unique row; a returned missing date removes only that projection. Unmatched retained provider metadata is not deleted. Failed remote refresh leaves prior metadata and events unchanged.

Home observes only Room. Its query joins active Library membership, starts seven calendar days before injected-clock today, and has no future cutoff. Library removal hides events without deleting them; re-add restores retained visibility and personal data. Groups sort by date, then episode, season, movie, normalized parent title, and provider-aware identity.

Remote refresh is explicit. It uses existing authenticated repositories with concurrency limit three. Movies refresh details. TV series refresh details and summaries, then seasons whose episode metadata was already cached, highest regular season, and regular seasons dated today or later. Season zero is selected only when its episodes were cached or its air date is within Home window. Seasons selected for one title are refreshed sequentially.

Results distinguish complete success, partial success, complete failure, no work, and credential required. Successful writes survive unrelated failures. Last-successful timestamp advances only after at least one eligible operation succeeds and local consistency completes; partial success may advance it. Empty Library, complete failure, credential failure, and local finalization failure do not fabricate success.

Release events and refresh state follow Room database platform-backup policy. Credentials remain in no-backup storage.

## Consequences

- Home opens offline from Room and never auto-refreshes remotely.
- Date-only events cannot shift with timezone conversion because no time or instant is fabricated.
- Retained non-member metadata costs storage but preserves fast re-add behavior.
- No generic scheduler, WorkManager worker, notification/reminder state, exact time, regional release selector, external calendar integration, provider-removal reconciliation, or Jikan behavior is introduced.
