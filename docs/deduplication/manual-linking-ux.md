# Manual Linking & Comparison UX Guidelines

## Overview

Milestone 14 Part 4 introduces the user-visible assisted-linking workflow in Bingee. The core design principles are:

1. **User Control**: Suggestions derived by Part 3 candidates are never automatically linked.
2. **Zero Mutation**: Linking changes only presentation grouping. Progress, ratings, Library memberships, and provider metadata remain independent.
3. **Reversibility**: Unlinking removes only the association. All underlying data remains intact.
4. **Isolated Search**: Remote and local search remain strictly provider-isolated.

---

## User-Facing Terminology

Natural language terminology is enforced throughout the application:

* `Possible duplicates` / `Possible duplicate`
* `Compare versions`
* `Link versions`
* `Linked versions`
* `Preferred version`
* `Change preferred version`
* `Separate versions`

---

## Candidate Presentation

Candidates are surfaced only in **Library** (top banner card when candidates exist) and **Details** (`Possible duplicate` card when an opposite-provider candidate exists for the current record).

Only `EXACT_IDENTITY` and `STRONG_POSSIBLE_SAME_WORK` classifications are shown. Ambiguous candidates are suppressed.

---

## Comparison Surface

The comparison bottom sheet presents:
- Both provider-specific records side-by-side or stacked.
- Clear provider attributions (e.g. TMDB Movie vs Jikan Anime).
- Structured positive signals explaining why Bingee suggested the pair (e.g., shared IMDb ID, exact title match, matching release year).
- Explicit radio selection for preferred presentation choice.
- Explicit non-destructive linking explanation.
- Re-validation of stale candidate state prior to executing `linkRepository.createLink`.

---

## Linked Library & Details Presentation

- **Library**: Grouped cards appear when at least one linked member is active in the Library. Displays the preferred member (or active fallback when preferred is inactive).
- **Details**: `Linked versions` section allows navigating to the other member, changing the preferred presentation, or unlinking with explicit confirmation.
- **Unlinking**: Fully reversible. Unlinking (`Separate versions`) deletes only the link association and audit trail without touching underlying media or personal records.
