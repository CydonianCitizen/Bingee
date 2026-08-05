# ADR 0018: TV Time import evidence and boundaries

- Status: Accepted
- Date: 2026-08-04

## Context

Milestone 12 may reduce migration cost for users with an external TV Time history, but an import parser is a data-integrity and privacy boundary. Before the continuation, the repository had no real TV Time export sample, provenance record, or reuse permission. Existing Bingee backup fixtures describe a different format.

The user supplied one complete ZIP for private local analysis. It contains three JSON top-level arrays and an auxiliary HTML report. The evidence is sufficient for one exact role-based source profile, but it does not establish behavior of other TV Time variants.

## Decision

Keep Milestone 12A as an evidence study and feasibility gate. Mark the named role-based profile as sufficient for a separate Milestone 12B implementation prompt, without claiming support for unobserved variants.

The profile must:

- validate ZIP safety before extraction;
- parse bounded UTF-8 JSON arrays without relying on private filenames;
- keep list, movie-like, and series/episode roles separate;
- qualify TVDB, IMDb, and TV_TIME identities;
- preserve nullable UTC-second watched_at and source precision;
- preserve season zero, specials, high season numbers, and nested parent context;
- treat ratings, original titles, air dates, and direct TMDB IDs as absent;
- classify unknowns, duplicates, conflicts, and unmatched list links;
- reject title-only automatic matching;
- require manual review for ambiguous records and always allow skip;
- keep raw personal evidence outside Git and release artifacts;
- apply any future database changes only after explicit confirmation in one transaction.

No production parser, Room migration, TMDB matching, Settings import UI, or external-history transaction is part of this ADR continuation.

## Consequences

- Milestone 12B can be planned for TVTIME-SAMPLE-001's exact role-based profile.
- Other application versions, locales, archive layouts, ratings, date variants, and duplicate rules remain unsupported until evidenced.
- The real ZIP and extracted files remain private local evidence.
- Committed fixtures must be independently synthetic and clearly documented.
- Existing Bingee backup parsing and replace-only restore semantics remain separate from external-history import.

