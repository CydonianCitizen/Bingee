# Milestone 14 — Part 3: Conservative Equivalence Candidate Detector

## 1. Overview & Principles

The Equivalence Candidate Detector is a local-first, deterministic domain and data layer component in Bingee. It evaluates locally persisted provider representations from TMDB (`MediaSource.TMDB`) and Jikan (`MediaSource.JIKAN`) to identify potential candidate pairs representing the same creative work.

### Core Guarantees:
1. **Local-First & Offline**: Operates strictly on existing local Room database projections. Executes zero network calls.
2. **Non-Destructive & In-Memory**: Derives candidate evaluations dynamically. Performs no database mutation, creates no candidate table, and saves no candidate state in DataStore or WorkManager.
3. **No Automatic Links**: Produces candidate pairs solely for voluntary user evaluation in Part 4.
4. **Cross-Provider Only**: Evaluates only pairs consisting of one TMDB entity and one Jikan entity. Same-provider matching is excluded.
5. **Active Link Exclusion**: Excludes any local media entity that already belongs to an active reversible link group (`media_link_groups` / `media_link_members`).
6. **Structured Reasons**: Uses explicit taxonomy classifications, positive signal sets, negative signal sets, and structured explanation reasons instead of opaque floating-point or percentage numerical scores.
7. **Strict Signal Discipline**: Title-only matching never emits a candidate. Release year and compatible media formats/types are mandatory. Progress, ratings, poster visual similarity, provider scores, and library timestamps are strictly forbidden matching inputs.

---

## 2. Classification Taxonomy & Semantics

| Classification | Meaning | Emission in `observeLibraryCandidates()` |
| :--- | :--- | :--- |
| `EXACT_IDENTITY` | Both records share a verified canonical IMDb identifier (`tt...`) with compatible format. | **Emitted** |
| `STRONG_POSSIBLE_SAME_WORK` | High metadata agreement (title + release year + compatible format + independent corroborating title/date signal) with no negative signals. | **Emitted** |
| `AMBIGUOUS` | Metadata is suggestive but incomplete (e.g. missing release year or single title signal). | *Suppressed* |
| `RELATED_DISTINCT` | Entries have documented relations (sequel, prequel, recap, remake) or multi-season granularity mismatch. | *Suppressed* |
| `NOT_EQUIVALENT` | Conflicting IMDb IDs, release year mismatch (>1 year), or incompatible media types/formats. | *Suppressed* |
| `INVALID_CANDIDATE` | Same-provider pair, identical identity, or member belongs to an active link group. | *Suppressed* |

> **Note**: The detector is intentionally conservative and may miss valid equivalents (expected false negatives). It must not create a false impression that similarity proves identity.

---

## 3. Candidate Indexing & Bounded Computation

To prevent $O(N^2)$ Cartesian product scans across large local libraries:
- Projections are pre-indexed in memory by:
  1. Canonical IMDb identifier
  2. Normalized primary title
  3. Normalized title variants
- Only plausible pairs sharing at least one index key are passed to `MediaEquivalenceEvaluator`.
- Output candidates are sorted deterministically: `EXACT_IDENTITY` before `STRONG_POSSIBLE_SAME_WORK`, with tie-breaking by provider-qualified identity keys.
