# ADR 0010: Keep TMDB movie and TV search separate

- Status: Accepted
- Date: 2026-08-01

## Context

Milestone 3 needs the first real remote-media flow without pre-empting details, persistence, or multi-provider design. TMDB exposes different movie and TV search endpoints and paginated response shapes. Result-list images also need a stable size and cache owner.

Official sources checked on 2026-08-01:

- [TMDB movie search](https://developer.themoviedb.org/reference/search-movie)
- [TMDB TV search](https://developer.themoviedb.org/reference/search-tv)
- [TMDB image basics](https://developer.themoviedb.org/docs/image-basics)
- [TMDB application authentication](https://developer.themoviedb.org/docs/authentication-application)
- [TMDB errors](https://developer.themoviedb.org/docs/errors)

## Decision

Expose Movies and TV Series through one Search screen with a Material single-choice segmented selector. Call GET /3/search/movie and GET /3/search/tv separately, with separate DTOs and mappers into the shared MediaSearchResult domain summary. Never merge or normalize the two provider rankings.

Normalize input by trimming leading and trailing whitespace only. One non-space character is meaningful. Preserve internal spaces and capitalization. Wait 350 milliseconds after input changes, cancel obsolete work, and use request-generation checks so stale responses cannot update state. Do not persist or log queries.

Send explicit language=en-US and include_adult=false. Begin at page 1 and use ViewModel-owned progressive loading rather than Paging 3. A Load more button requests the next page. Keep provider ordering, deduplicate by ExternalMediaRef, retain existing results during or after page failure, retry the failed page, and stop at total pages, TMDB's documented page-500 limit, or no new usable rows.

Reuse the encrypted credential store inside TmdbSearchClient and attach its Bearer header only at the data/network boundary. Unauthorized search responses do not delete or reclassify the credential; they return AppError.Unauthorized and let the Search UI offer Settings.

Resolve validated TMDB poster paths to the documented CDN pattern at w342. Use Coil 3.4 for constrained Compose loading and its memory/disk caches. Coil shares the application's existing OkHttpClient; it does not receive the TMDB credential. Missing, invalid, or failed posters use one local accessible placeholder.

Skip only rows without a positive TMDB ID or without both localized and original title. Fall back from a missing localized title to the original title. Keep optional poster, overview, and valid parsed LocalDate values; malformed dates become absent.

## Consequences

- Movie and TV provider differences stay explicit while UI and ViewModel depend only on domain models.
- Search has no details route, library action, Room persistence, offline result cache, history, or Jikan behavior.
- Pagination remains small and directly testable; Paging 3 can be reconsidered only after measured need.
- Search uses fixed English (United States) metadata and excludes adult results until a later product decision introduces a localized or age-policy setting.
- MediaDetails remains distinct and untouched for its later milestone.
