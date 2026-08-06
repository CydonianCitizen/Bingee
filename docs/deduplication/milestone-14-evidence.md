# Milestone 14 — Cross-Provider Equivalence Evidence & Signal Analysis

## 1. Overview and Intent

This document summarizes empirical evidence, user context, and signal evaluation regarding cross-provider media duplicates in Bingee (TMDB Movies/TV Series and Jikan Anime). 

Per `AGENTS.md` and ADR 0020 / ADR 0021:
- Bingee is local-first, privacy-preserving, and non-destructive.
- TMDB and Jikan maintain isolated external identities (`MediaSource.TMDB` vs `MediaSource.JIKAN`).
- Automatic deduplication based solely on titles or naive heuristics is strictly prohibited.

---

## 2. Evidence Inventory (Safe Aggregate Overview)

| Evidence ID | Provider A (Type) | Provider B (Type) | User Context & Perception | Structural Relationship |
| :--- | :--- | :--- | :--- | :--- |
| `M14-CASE-001` | TMDB (MOVIE) | JIKAN (ANIME) | Same theatrical feature film | `SAME_WORK` (Shared IMDb ID `tt0245429`) |
| `M14-CASE-002` | TMDB (SERIES) | JIKAN (ANIME) | Top-level franchise vs S1 anime | `POSSIBLE_SAME_WORK` (Granularity Mismatch: TMDB groups all seasons; Jikan splits per season) |
| `M14-CASE-003` | TMDB (SERIES) | JIKAN (ANIME) | TMDB Season 2 vs Jikan S2 entry | `RELATED_DISTINCT` (Season-to-entry mapping; structural incompatibility) |
| `M14-CASE-004` | TMDB (MOVIE) | JIKAN (ANIME) | Anime theatrical continuation | `SAME_WORK` (Shared IMDb ID `tt11032374`) |
| `M14-CASE-005` | JIKAN (ANIME) | JIKAN (ANIME) | Original 2003 vs 2009 reboot | `RELATED_DISTINCT` (Remake / Reboot relationship) |
| `M14-CASE-006` | TMDB (SERIES) | TMDB (SERIES) | Title collision (different year/show) | `DEFINITELY_SEPARATE` (Homonym / Unrelated work) |

**Privacy & Permission Status**:
- All private local history stays inside `.local-evidence/` (Git-ignored).
- Zero user tokens, personal progress values, or raw API keys are committed or transmitted.
- No background telemetry, analytics, or query tracking SDK is present.

---

## 3. Observed Duplicate Contexts in Bingee

1. **Library Presentation**: Users adding both TMDB and Jikan entries for the same animated film or series see two distinct cards in their Library.
2. **Search Discovery**: Switching tabs between Movies/TV Series (TMDB) and Anime (Jikan) can yield corresponding entries for popular animated works.
3. **Details & Navigation**: Jikan anime details show relations (prequel, sequel, movie version) which may correspond to separate TMDB entries or TMDB TV seasons.

---

## 4. Matching Signal Taxonomy

### Strong Identity Signals (High Confidence)
- **Shared External Reference**: Verifiable common IMDb ID (e.g. `tt0245429`) present in both TMDB and Jikan provider metadata.
- **User Confirmation**: Explicit manual verification by the user.

### Supporting Signals (Medium Confidence — Require Combination)
- **Title Agreement**: Exact or normalized title match across primary, original (Japanese), or English title fields.
- **Release Date / Year**: Exact release date or identical release year.
- **Compatible Media Format**: TMDB MOVIE matching Jikan Anime (Movie format); TMDB SERIES matching Jikan Anime (TV format).

### Negative Signals (Confidence Lowering / Rejection Criteria)
- **Granularity Mismatch**: One record is a multi-season series (TMDB) while the other is a specific season entry (Jikan).
- **Explicit Provider Relation**: Prequel, sequel, spin-off, side-story, recap, or OVA relationship defined in provider metadata.
- **Release Year Divergence**: Difference of >1 year without documented regional delay evidence.
- **Conflicting External Identifiers**: Mismatched IMDb IDs.

### Forbidden Signals (Never Sufficient by Themselves)
- Title similarity or normalized title match alone.
- Poster visual similarity.
- Numeric provider ID coincidence without provider namespace qualification.
- Provider popularity, score, or rating.
- User library status or watch progress.

---

## 5. Evidence Gate Outcome

- **Real-usage duplicates exist primarily in animated feature films and single-season anime TV series.**
- **Structural granularity mismatches (TMDB multi-season series vs. Jikan per-season entries) make automatic merging unsafe and destructive.**
- **Assisted, user-controlled linking is justified; automatic linking or automatic data merging is rejected.**
