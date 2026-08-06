# Unified Search Decision & Architectural Recommendation

## 1. Context & Trade-Off Analysis

ADR 0020 established three explicit, provider-isolated search categories:
- **Movies** (TMDB)
- **TV Series** (TMDB)
- **Anime** (Jikan)

Milestone 14 Part 1 evaluates whether creating a unified "All" or merged cross-provider Search list is useful and safe for Bingee users.

---

## 2. Evaluation of Options

### Option A — Keep Separate Search Categories (RECOMMENDED)
- **Behavior**: Maintain isolated Movies, TV Series, and Anime tabs. Duplicate discovery occurs via "Possible Duplicate" suggestions inside Title Details or Library screens.
- **Pros**:
  - Predictable error isolation: TMDB key missing/invalid error stays in Movies/TV; Jikan rate-limit stays in Anime.
  - Clean pagination: Independent page numbers and scroll states.
  - Zero ambiguity: User knows exactly which catalogue they are querying.
- **Cons**: User must switch tabs if searching for an animated title across both catalogues.

### Option B — Add an Optional "All" Category
- **Behavior**: Execute parallel requests to TMDB and Jikan, combining results into one list.
- **Pros**: Single query returns results from both providers.
- **Cons**:
  - Asymmetric error states: If Jikan fails, list shows partial results without clear provider context.
  - Rate-limit & latency mismatch: Jikan API (read-only, rate limited to ~3 req/sec) vs TMDB API.
  - Confusing pagination: Disparate page sizes (TMDB 20 items vs Jikan 25 items).
  - High duplication in result lists for popular animated feature films.

### Option C — Unified Presentation After Manual Links Only
- **Behavior**: Search remains separate, but linked items show a link badge.
- **Pros**: Clean, non-disruptive, preserves isolated search dispatch.
- **Cons**: Does not alter raw search result list ordering.

### Option D — No Unified Search (Assisted Library Linking Only)
- **Behavior**: Search is strictly separate. Linking applies only to entities persisted in local Library / Database.
- **Pros**: Safest, local-first, minimal complexity, 100% predictable.

---

## 3. Evidence-Based Recommendation

> **RECOMMENDATION: Option A / Option D (Keep Provider-Isolated Search Categories).**
> **Do NOT implement a unified mixed Search list.**

### Key Reasons:
1. **Credential & Availability Asymmetry**: TMDB requires a user-configured API key; Jikan is unauthenticated and rate-limited. Merging search causes broken UX when TMDB key is unconfigured or when Jikan experiences rate-limiting.
2. **Granularity Disparity**: TMDB TV series search returns whole shows; Jikan anime search returns individual seasons/ONAs/OVAs as top-level search results. Mixing them creates an unbalanced, confusing result list.
3. **User Intent**: Users searching for "Anime" intentionally select the Anime tab for MyAnimeList metadata, while users searching for mainstream movies select Movies.

Assisted cross-provider linking should be restricted to **Title Details** and **Library**, where persisted local records can be analyzed safely.
