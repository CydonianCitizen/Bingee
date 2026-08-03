# 0012: Cache-first TMDB details and Room v2

- Status: Accepted
- Date: 2026-08-03

## Context

Search summaries are transient and Library v1 stores only canonical list metadata plus membership. A title detail must render saved text immediately, avoid needless requests, survive refresh failure, and support non-member titles without weakening provider-qualified identity or credential isolation.

Official sources verified on 2026-08-03:

- [TMDB movie details](https://developer.themoviedb.org/reference/movie-details)
- [TMDB TV series details](https://developer.themoviedb.org/reference/tv-series-details)
- [TMDB application authentication](https://developer.themoviedb.org/docs/authentication-application)
- [TMDB language behavior](https://developer.themoviedb.org/docs/languages)
- [TMDB image language behavior](https://developer.themoviedb.org/docs/image-languages)
- [Android Room migrations, schema export, and migration tests](https://developer.android.com/training/data-storage/room/migrating-db-versions)
- [Android Room database testing](https://developer.android.com/training/data-storage/room/testing-db)

## Decision

Use separate TMDB movie and TV DTOs and explicit mappers into the shared `MediaDetails` domain model. Request only base `GET /3/movie/{movie_id}` and `GET /3/tv/{series_id}` responses with the same `en-US` policy as Search. Do not append or request seasons, episodes, credits, videos, recommendations, or providers.

Room `bingee.db` advances from version 1 to 2 through explicit `MIGRATION_1_2`. Add:

- `media_details`, keyed by `local_media_id`, for normalized detail-only fields plus ISO UTC `details_fetched_at`;
- `media_genres`, keyed by (`local_media_id`, `genre_order`), for deterministic ordered names.

Both tables use cascading foreign keys to `media_entries`. One transaction resolves or creates canonical identity, preserves external references and membership, updates canonical metadata, replaces detail metadata and genres, then records the successful fetch timestamp. Migration creates empty tables only; it performs no network work and fabricates no freshness.

Freshness is 24 hours through an injectable UTC `Clock`. Age less than 24 hours is fresh; exactly 24 hours is stale; future timestamps are stale. Fresh cache skips automatic refresh. Stale cache displays immediately while refresh runs. Cache miss shows loading. Manual refresh bypasses freshness. A failed refresh never clears visible or persisted cache and never advances the timestamp. Concurrent refreshes for one reference are coalesced; different references remain independent.

Detail cache and Library membership have separate ownership. Opening may cache a non-member. Removing membership retains details, genres, canonical metadata, and external references. No pruning exists yet.

The centralized non-top-level route carries `MediaSource`, `MediaType`, and external ID only. Media type is necessary for a Search-origin title with no local row. Unsupported sources fail before a TMDB request.

## Consequences

Textual details reopen offline and remain available after credential removal. Images rely on Coil cache or accessible fallback; image bytes are not stored in Room. Room schema versions 1 and 2 remain version controlled and migration tests use the v1 export.

No season/episode list, watch state, rating, cast/credits, recommendations, background refresh, cache pruning, or Jikan behavior is introduced.
