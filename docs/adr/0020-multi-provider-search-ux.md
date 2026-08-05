# ADR 0020: Provider-isolated search categories

- Status: Accepted
- Date: 2026-08-05

## Context

Bingee already searches movies and TV series through TMDB. Milestone 13 adds anime metadata from Jikan, an unofficial read-only MyAnimeList API. Provider availability, identity and terms differ, so a mixed result list would make failures and identity ambiguous.

## Decision

Search has three explicit categories: Movies, TV Series, and Anime. Movies and TV Series call only TMDB; Anime calls only Jikan. The selected category owns request, loading, pagination, error, retry, and cancellation state. Switching category cancels obsolete work and generation checks reject stale results.

There is no mixed ranking, title-equivalence hint, automatic cross-provider deduplication, or provider fallback. A Jikan failure is shown only in Anime and a missing TMDB credential is shown only in TMDB categories. Anime result/detail UI identifies Jikan/MyAnimeList provenance where useful; TMDB UI remains unchanged.

## Consequences

The first implementation duplicates only the small provider dispatch boundary instead of inventing a generic provider platform. Users choose the catalogue deliberately, numeric provider IDs cannot be confused, and either provider can be unavailable without disabling the other. Unified discovery or reversible linking requires a separately specified future milestone.

## Official references verified 2026-08-05

- Jikan: [REST API v4](https://docs.api.jikan.moe/) documents `https://api.jikan.moe/v4/`, read-only GET access, `/anime`, `/anime/{id}/full`, pagination, error responses, 24-hour server caching, and that Jikan is unofficial and not affiliated with MyAnimeList.
