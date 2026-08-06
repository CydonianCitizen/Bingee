# Equivalence Taxonomy & Classification Rules

## 1. Overview

This document specifies the taxonomy used by Bingee to classify candidate pairs across media providers (TMDB and Jikan).

Equivalence classification determines whether two provider records may be suggested for linking or linked manually by the user.

---

## 2. Taxonomy Classes

### 2.1 `EXACT_IDENTITY` (formerly `SAME_WORK`)
- **Definition**: Both provider entries represent the identical creative work (e.g. an animated feature film on TMDB Movies and Jikan Anime).
- **Linking Eligibility**: User-confirmable link candidate.
- **Criteria**: Shared verified external identity (e.g., matching canonical IMDb ID) and compatible format.

### 2.2 `RELATED_DISTINCT`
- **Definition**: The entries have a documented creative or franchise relationship but are distinct media entities.
- **Examples**:
  - Source material vs adaptation.
  - Season 1 vs Season 2 / Sequel / Prequel.
  - TV Series vs Recap Movie / Compilation.
  - Main Series vs OVA / Special / Spin-off.
  - Original work vs Remake / Reboot.
- **Linking Eligibility**: **PROHIBITED** from cross-provider equivalence linking. Must remain separate entries.

### 2.3 `POSSIBLE_SAME_WORK`
- **Definition**: Metadata signals strongly suggest potential equivalence, but lack a definitive shared identity anchor (e.g. missing IMDb ID).
- **Linking Eligibility**: System may surface a "Possible Duplicate" suggestion in UI for explicit user evaluation. Never auto-linked.

### 2.4 `AMBIGUOUS`
- **Definition**: Signals conflict or essential metadata (such as release year or format) is missing.
- **Linking Eligibility**: Suppressed from suggestions. User may search and link manually if they choose.

### 2.5 `NOT_EQUIVALENT`
- **Definition**: Provider records represent unrelated works despite potential surface similarities (e.g., homonym titles in different years).
- **Linking Eligibility**: Prohibited from linking.

### 2.6 `INVALID_CANDIDATE`
- **Definition**: Candidate pair involves incompatible categories or identical provider identities (e.g., comparing a TMDB movie to itself or comparing non-supported types).
- **Linking Eligibility**: Rejected immediately.

---

## 3. Decision Matrix

```
[Candidate Pair (Entity A, Entity B)]
        │
        ├─► Identical Provider & ID? ──► [INVALID_CANDIDATE]
        │
        ├─► Conflicting / Distinct IMDb IDs? ──► [NOT_EQUIVALENT]
        │
        ├─► Verified Shared IMDb ID? ──► [SAME_WORK]
        │
        ├─► Provider Relation = Sequel/Prequel/OVA/Spin-off/Remake? ──► [RELATED_DISTINCT]
        │
        ├─► Title Match + Same Year + Compatible Format + No Conflicts? ──► [POSSIBLE_SAME_WORK]
        │
        ├─► Missing Year or Conflicting Format? ──► [AMBIGUOUS]
        │
        └─► Divergent Title / Year / Type? ──► [NOT_EQUIVALENT]
```

---

## 4. Synthetic Design Fixtures

1. **Fixture `FIX-SAME-01` (`SAME_WORK`)**:
   - Entry A: TMDB Movie `129` (*Spirited Away*, 2001, IMDb `tt0245429`)
   - Entry B: Jikan Anime `199` (*Sen to Chihiro no Kamikakushi*, 2001, Movie, IMDb `tt0245429`)
   - Result: `SAME_WORK`

2. **Fixture `FIX-REL-01` (`RELATED_DISTINCT`)**:
   - Entry A: Jikan Anime `16498` (*Attack on Titan S1*, 2013)
   - Entry B: Jikan Anime `25777` (*Attack on Titan S2*, 2017)
   - Result: `RELATED_DISTINCT` (Sequel relationship)

3. **Fixture `FIX-POSS-01` (`POSSIBLE_SAME_WORK`)**:
   - Entry A: TMDB Movie `447362` (*My Hero Academia: Two Heroes*, 2018)
   - Entry B: Jikan Anime `37441` (*Boku no Hero Academia Movie 1: Futari no Hero*, 2018, Movie format)
   - Result: `POSSIBLE_SAME_WORK` (High title/year/format agreement, no IMDb anchor in cache)

4. **Fixture `FIX-NOT-01` (`NOT_EQUIVALENT`)**:
   - Entry A: TMDB TV `38757` (*Monster*, 2004)
   - Entry B: TMDB TV `94269` (*Monster*, 2023)
   - Result: `NOT_EQUIVALENT` (Year divergence: 2004 vs 2023)
