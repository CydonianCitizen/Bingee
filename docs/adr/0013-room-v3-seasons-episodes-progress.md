# ADR 0013: Room v3 seasons, episodes, and local watch progress

## Status

Accepted

## Context

Milestone 6 adds TV-season metadata, incrementally cached episodes, episode progress, and movie watched state. Provider metadata must be refreshable without overwriting personal history, and loaded data must remain useful without a credential, network, or Library membership.

## Decision

Room version 3 adds normalized `seasons`, `episodes`, `episode_watch_progress`, and `movie_watch_progress` tables. Migration 2-to-3 creates empty structures and preserves every v2 row. The registered 1-to-2-to-3 path remains non-destructive.

Season and episode rows contain provider-qualified identity, parent foreign keys, numbering metadata, normalized display metadata, and refresh timestamps. Provider IDs are the stable identity; season and episode numbers are unique only within their local parent. Refresh upserts returned rows and conservatively retains unmatched local rows. It neither fabricates episode rows from summaries nor performs provider-removal reconciliation.

Personal state is represented only by progress-row presence plus one `watched_at` timestamp. It is absent from provider DTOs, media, detail, season, episode, and Library-membership rows. Movie progress references a movie directly; no synthetic episode is created. Removing Library membership retains metadata and progress.

TV details persist returned season summaries, including season zero. Episode metadata loads per season on expansion. A cache younger than 24 hours is fresh; exactly 24 hours and future timestamps are stale. Cached episodes remain visible during refresh, and only successful atomic persistence updates `episodes_fetched_at`. Refreshes coalesce per series and season; unrelated seasons may proceed independently.

An episode is trackable when its air date is absent or no later than the current UTC date from the injected clock. Future episodes remain stored but cannot be marked watched. A bulk watched action marks only trackable episodes, gives newly watched rows one action timestamp, and retains existing watched timestamps. Bulk unwatched affects only the selected season, including season zero when selected.

Season completion requires at least one trackable episode and all trackable episodes watched. Series completion uses only regular seasons numbered above zero, requires at least one regular trackable episode, and excludes specials. Season-zero progress remains visible separately. Completion, counts, and fractions are derived, never persisted.

Local progress operations need neither TMDB credential nor network and work for cached non-members. Remote season refresh still requires the protected TMDB boundary. No season-detail or episode-detail navigation route is introduced.

## Consequences

- A metadata refresh cannot erase matching progress; unmatched episodes and seasons remain until a future explicit reconciliation policy exists.
- Newly synchronized trackable episodes can make a previously complete season or series incomplete.
- Unknown-air-date episodes are intentionally trackable; changing this policy requires tests and a new decision.
- Cached season data can grow because cache pruning and provider-removal reconciliation are deferred.
- Ratings, release events, background refresh, notifications, JSON import/export, and Jikan are not part of this decision.
