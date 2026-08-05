# TV Time import evidence requirements

Status: evidence gate passed for one exact role-based variant; broad variant support remains unproven.

Reviewed: 2026-08-04

## Purpose

Milestone 12A is a feasibility gate for a possible TV Time history import. It is not parser implementation. The gate prevents a production import path from being based on screenshots, remembered columns, forum descriptions, guessed schemas, or synthetic data.

## Current gate result

| Gate | Finding for TVTIME-SAMPLE-001 | Status |
| --- | --- | --- |
| Sample existence | Complete user-supplied ZIP; 3 JSON roles plus auxiliary HTML | PASS |
| Provenance | Safe ID and local-only permission boundary recorded | PASS |
| Privacy | Raw values excluded from reports and Git; category scan completed | PASS |
| Legal usability | Private analysis permitted; real/close derivative redistribution absent | PASS for local study |
| Format sufficiency | Exact role-based JSON grammar documented; one source variant only | PASS for scoped profile |

The result is narrow:

Milestone 12A complete — evidence sufficient for Milestone 12B implementation

This does not authorize production implementation in the continuation. It authorizes a separate Milestone 12B prompt for the named profile.

## Verified profile

The supplied ZIP has:

- UTF-8 without BOM and LF line endings;
- three top-level JSON arrays;
- list, movie-like, and series/episode roles;
- TVDB integer IDs, IMDb strings/nulls, and opaque UUIDs;
- watched booleans and UTC-second watched_at values;
- season zero and explicit specials;
- nullable watched_at for unwatched records;
- repeated-watch counters;
- no rating, original-title, episode-air-date, or direct TMDB field;
- an auxiliary HTML report that is not parser input.

See tv-time-source-format-v1.md for the exact contract and tv-time-format-study.md for evidence counts.

## Additional evidence recommended

These are not blockers for the one named profile, but are required before claiming broad compatibility:

- a distinct application/export version;
- a second locale or date grammar;
- an empty export;
- duplicate/conflicting source records;
- a watched record with missing timestamp, if the source can produce one;
- ratings, original titles, or air dates, if the source can export them;
- alternate archive layout or filename conventions.

New behavior must be added as verified evidence or a new source profile, never as an assumption.

## Safety boundary

No production parser, source DTO, Room write path, TMDB matcher, Settings action, import ViewModel, or external-history transaction was added while this gate was studied. Synthetic fixtures document structure only and are not evidence for other variants.

