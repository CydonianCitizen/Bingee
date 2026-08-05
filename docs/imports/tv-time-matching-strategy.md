# TV Time matching strategy

Status: implemented for the experimental Milestone 12B profile only.

Reviewed: 2026-08-04  
Evidence: TVTIME-SAMPLE-001

## Matching principles

- Stable, qualified provider identity outranks title text.
- Title-only automatic matching is prohibited.
- Media type is mandatory.
- Year is used when available and must not be ignored.
- Episode matching must include series, season, and episode context.
- Season zero and explicit special flags remain distinct.
- Uncertain matches require manual review.
- Skip is always available.
- No TMDB request was made during Milestone 12A.

## Source identities observed

| Source signal | Scope | Directly usable against Bingee TMDB identity? | Use |
| --- | --- | --- | --- |
| TVDB integer | movies, series, episodes, list links | no direct TMDB ID; official TMDB Find supports TV/season/episode, but not movie TVDB IDs | exact source identity; resolver input only for supported media types |
| IMDb string | movie records only | no direct TMDB ID; may be resolved by a documented TMDB/IMDb path | exact source identity and resolver input |
| TV_TIME UUID | title records and movie list links | no | source identity and cross-file relationship |
| title | movies, series, episodes | no | search/display hint only |
| movie year | movie-like records | no | corroborating signal |
| season/episode number | nested episodes | no | context signal |
| episode name | episodes | no | corroborating signal |
| status/favorite | series/title | no | review context; not identity |

No TMDB ID, TMDB namespace, or direct cross-provider key is present.

## Confidence classes

### Exact source identity

An exact source identity exists when a qualified TVDB or IMDb value is resolved to one TMDB candidate through the implemented provider-aware, documented path, with compatible media type. The source TV_TIME UUID is exact only for cross-file source relationships; it is not an exact TMDB identity. A movie TVDB value is provenance only and is not queried through TMDB Find because the official support matrix does not provide that combination.

An exact source ID alone must not bypass media-type validation.

### High-confidence composite

Use only with a unique candidate and all required context:

- movie: no resolvable exact IMDb identity + normalized title equality + exact year + exactly one compatible TMDB movie candidate + no conflicting qualified identity;
- series: no title-only automatic rule; without one uniquely resolved qualified identity it remains manual;
- episode: resolved parent series + season number + episode number + optional episode-name corroboration;
- special: same as episode, plus explicit special/season-zero agreement.

The current sample lacks a series year and episode air date. Those signals cannot be fabricated.

### Ambiguous

Manual review required when:

- multiple TMDB candidates share title/type/year;
- provider identity resolves to multiple candidates;
- a title is a remake/reboot or localized collision;
- season number is high or numbering conflicts;
- special flags disagree;
- a list link has no matching record;
- source role is inferred rather than explicit and candidate types conflict;
- existing Bingee timestamps conflict with imported state (reported during preview while the local value wins).

### Unmatched

No safe candidate, no provider-aware identity resolution, or only a title search result without corroboration. User may skip.

### Invalid

Malformed JSON, duplicate property keys, missing required structural fields, wrong primitive types, invalid timestamp grammar, unsafe archive, or impossible relationship context.

## Cross-file matching

The observed list relationships are deterministic within the source archive:

- 4 series list items use TVDB IDs and match 4 series records;
- 3 movie list items use TV_TIME UUIDs; 2 match movie records and 1 remains unresolved;
- no list TVDB ID maps to a movie;
- no list UUID maps to a series.

The unresolved movie list item must generate a warning and cannot be silently attached by title.

## Milestone 12B implementation status

The documented rules are implemented through the existing authenticated TMDB clients. `EXACT` requires one compatible qualified external-ID result; `HIGH_CONFIDENCE` is limited to a unique movie normalized-title plus exact-year candidate. A title-only series result is always `AMBIGUOUS`. Watched regular episodes require an accepted parent series plus one season request and unique season/episode numbering. Specials remain manual unless a qualified episode ID resolves uniquely. Exact and high-confidence proposals may be bulk accepted; ambiguous and unmatched records require candidate selection or explicit skip. No network request runs inside the final Room transaction.

## Episode matching

Required context:

1. matched parent series;
2. source season number;
3. source episode number;
4. special/is_specials agreement when either side marks a special;
5. source episode TVDB identity when available.

Episode title is corroboration, not sole identity. High season numbers must remain source numbers. A season-zero episode cannot silently match regular season one. The source has no episode air date and no explicit parent ID inside the episode object; nesting supplies parent context.

## Personal-state conflict policy for 12B

The implemented final Room merge policy:

- preserve existing watched state;
- never turn a watched item unwatched automatically;
- expose timestamp conflicts;
- preserve source counters separately until a deliberate conversion is defined;
- require a source watched timestamp for watched progress; a missing title `created_at` may use confirmation time only for new Library membership and must be reported as an approximation;
- show rating as unsupported for this profile;
- commit only the user's confirmed accepted set in one transaction;
- keep skipped/unmatched/invalid records out of the transaction;
- make repeated imports idempotent through qualified source identity plus resolved local identity.

## Review output

Each preview row should expose, without leaking raw personal data into logs:

- safe source location;
- media type;
- source identities by namespace;
- title/year/season/episode hints in the UI;
- candidate and confidence class;
- reason for ambiguity or rejection;
- imported personal-state fields;
- skip action.

Logs and crash reports must contain safe locations and categories only.

## Known gaps

The sample does not prove:

- alternate locale/title behavior;
- source duplicates or precedence;
- missing watched timestamp for a watched record;
- direct TMDB/TVDB resolution availability;
- a rating scale;
- an air-date fallback;
- other archive layouts.

Those gaps require new evidence or explicit Milestone 12B policy decisions. They do not authorize title-only matching.
