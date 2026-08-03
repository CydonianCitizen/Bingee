# ADR 0014: Room v4 title ratings and local Library organization

## Status

Accepted

## Context

Milestone 7 completes the minimum personal title model and makes a growing Library usable without network or credential access. Ratings must remain independent from provider metadata, membership, and watch progress. Library states must react to local progress without becoming stale persisted flags.

## Decision

Room version 4 adds `media_ratings`, with one row keyed by local media ID, a required integer value, `rated_at`, and `updated_at`. The foreign key targets canonical `media_entries`; no Library-membership, episode, season, review, social, or provider field exists. Domain, repository, DAO, and entity boundaries accept only values 1 through 10. Room entity annotations cannot express a portable `CHECK` constraint while keeping fresh and migrated schemas identical, so the explicit migration creates the same structure and validation occurs before every DAO write. Migration 3-to-4 creates an empty table and preserves all v3 data. Migrations 1-to-2, 2-to-3, and 3-to-4 remain explicitly registered without destructive fallback.

The first rating sets both timestamps. A changed rating preserves `rated_at` and advances `updated_at` using the injected UTC clock. Setting the same value and removing an absent rating are logically idempotent and do not change timestamps. Removing Library membership, changing progress, refreshing metadata, or removing the TMDB credential does not remove a rating.

Movie Library state is `NOT_STARTED` or `COMPLETED`. TV state is `PROGRESS_UNAVAILABLE`, `NOT_STARTED`, `IN_PROGRESS`, or `COMPLETED`. TV derivation uses regular trackable episodes: season zero and future episodes are excluded; unknown-air-date episodes remain trackable. States and completion ratios are never persisted.

`LibraryDao` restricts results to active membership, optional media type, and localized/original-title matching. Query input is trimmed, lowercased with a locale-independent policy, and `%`, `_`, and the escape character are escaped before `LIKE ... ESCAPE '\\'`; internal spaces remain unchanged. SQLite case folding is deterministic for ASCII, while non-ASCII text remains safely searchable by exact normalized code points. Search state remains in the Library ViewModel only and is neither logged nor sent to TMDB.

The repository combines observable membership rows, derived progress counts, and active ratings. Domain logic applies derived-state filters and deterministic sorting. Sorts are recently added, title, progress, and personal rating. Progress order is in-progress, not-started/unwatched, completed/watched, then unavailable; completion ratio descending breaks ties within a state. Rating sort places rated titles first and higher values first. All sorts then use normalized title, original title, source, and external ID as stable tie-breakers. Selecting Movies resets impossible in-progress or unavailable state filters to All.

Title details use an integer-stepped 1–10 Material slider with explicit value, save, and remove actions. Rating edits remain title-level and local. No new destination or route payload is introduced.

## Consequences

- Ratings and Library organization work from Room without a network or TMDB credential.
- Library typing stays explicit; no generic query builder, arbitrary filter map, or persisted derived state is introduced.
- Leading-wildcard title matching favors correctness and small personal-library scale over a speculative full-text index.
- Minimal statistics are deferred because they add UI density without improving the milestone's core organization flows.
- Fractional, episode, season, social, written-review, tag, favorite, custom-list, remote-rating, and cloud-sync behavior remain unsupported.
