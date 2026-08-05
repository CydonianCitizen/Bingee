# TV Time format study

Status: complete for one explicitly documented source variant; no claim is made about other TV Time export variants.

Reviewed: 2026-08-04

## Scope and evidence boundary

This is a bounded evidence study, not production import code. The source ZIP was supplied by the user in the private local evidence boundary '.local-evidence/tv-time/'. It was inspected and extracted locally only. No upload, web request, TV Time request, TMDB request, or third-party service call was made.

Safe sample identifier: TVTIME-SAMPLE-001.

The original archive, extracted records, source filenames, identifiers, titles, watch history, ratings, and timestamps are private evidence. None is copied into this document or Git. The archive digest is retained only in the ignored local analysis note.

## Verified archive and inventory

The archive passed conservative pre-extraction checks:

- 8,800,587 bytes;
- 4 central-directory entries;
- 8,800,029 uncompressed bytes;
- largest entry 8,128,002 bytes;
- maximum observed compression ratio 1.00x;
- no encryption, traversal, absolute path, drive path, null-byte filename, normalized-path collision, symlink-like entry, or nested archive.

The four entries are referred to only by safe identifiers because private source filenames are not reportable.

| Safe entry | Type | Bytes | Verified structure | Approximate records | Role | Relevance |
| --- | --- | ---: | --- | ---: | --- | --- |
| ENTRY-001 | JSON | 1,188 | UTF-8, no BOM, LF; top-level array | 1 list object; 7 list items | custom-list metadata and links | relevant auxiliary data |
| ENTRY-002 | JSON | 79,309 | UTF-8, no BOM, LF; top-level array | 238 objects | movie-like title records | primary |
| ENTRY-003 | JSON | 8,128,002 | UTF-8, no BOM, LF; top-level array | 134 series, 975 seasons, 22,666 episodes | series and episode records | primary |
| ENTRY-004 | HTML | 591,530 | UTF-8, no BOM, LF; readable HTML report | presentation-dependent | derived report with tables and links | ignored by parser |

No CSV, TSV, XML, SQLite, plain-text data export, image/media asset, or nested archive was observed. All three JSON documents parsed successfully. A bounded duplicate-property scan found zero duplicate JSON object keys in each JSON document.

## Verified grammar

All selected machine-readable files are ordinary JSON, not JSON Lines. Each has a top-level array. The observed source uses LF line endings and UTF-8 without a BOM. Strings use standard JSON quoting and escaping. No malformed row, truncated document, duplicate property key, comment, or non-JSON scalar was observed.

### List role (ENTRY-001)

The array contains one object:

~~~
id: string
name: string
description: string
is_public: boolean
created_at: string timestamp
items: array<object>
~~~

Each item contains:

~~~
custom_order: integer
name: string
type: string            # observed categories: movie, series
tvdb_id: integer?      # present for 4 of 7 items
uuid: string?          # present for 3 of 7 items
~~~

The list description was an empty string. Optionality is observed presence, not an official-schema claim.

### Movie-like title role (ENTRY-002)

The array contains 238 objects. Every observed object has:

~~~
created_at: string timestamp
id: object { imdb: string, tvdb: integer }
is_favorite: boolean
is_watched: boolean
rewatch_count: integer
title: string
uuid: string
watched_at: string timestamp | null
year: integer
~~~

No record-level media-type field is present. Movie role is a strong structural inference from the separate title array, year, and absence of seasons/episodes; the future parser must preserve it as source-role metadata rather than invent a source flag. Unicode and commas occur in titles; no movie title with a quote or embedded newline was observed.

### Series and episode role (ENTRY-003)

The array contains 134 objects. Every observed series object has:

~~~
_noEpisodeData: boolean
created_at: string timestamp
id: object { imdb: null, tvdb: integer }
is_favorite: boolean
seasons: array<object>
status: string
title: string
uuid: string
~~~

_noEpisodeData was false for every series; its production meaning is opaque.

Each season has:

~~~
episodes: array<object>
is_specials: boolean
number: integer
~~~

Each episode has:

~~~
id: object { imdb: null, tvdb: integer }
is_watched: boolean
name: string
number: integer
rewatch_count: integer
special: boolean
watched_at: string timestamp | null
watched_count: integer
~~~

Observed: season zero, explicit special flags, 118 season numbers at or above 1000 (none with is_specials), 3 empty episode arrays, and no empty season array. High season numbers must not be clamped or mapped to season zero. Episode names include Unicode, commas, and quotes.

### HTML role (ENTRY-004)

HTML is a derived report artifact with tables, headers, controls, scripts, styles, and public-host links. It has no JSON reference and is excluded from the parser contract. Its links had no query string, fragment, user-info component, or authentication keyword.

## Encoding, dates, and nulls

| Field | Observed representation | Verified counts | Interpretation |
| --- | --- | ---: | --- |
| created_at | ISO-8601 ending in Z | 373 non-null strings | source creation metadata; precision varies |
| watched_at | YYYY-MM-DDTHH:mm:ssZ | 6,412 strings; 16,492 nulls | one watch timestamp when watched |

All observed watched movies/episodes with is_watched=true have a string watched_at. All observed unwatched records have null. No watched record with a missing timestamp was observed; this is not generalized to other exports.

All watched_at values have UTC Z and second precision. created_at also uses UTC Z but is mixed: second precision plus observed four-, five-, and six-digit fractional precision. No date-only, localized date, epoch, numeric offset, invalid date, or non-UTC timezone was observed.

## Identifier findings

- id.tvdb and list tvdb_id are JSON integers: 23,038 title/series/episode values plus 4 list links.
- id.imdb is a string matching the observed tt-plus-digits shape for 238 movie records; it is null for every series and episode.
- uuid is a canonical UUID string; movie and series root UUIDs are unique, with 3 distinct list-item UUIDs.
- Movie and series TVDB IDs do not collide; episode TVDB IDs are unique; no duplicate root UUID or provider ID was observed.

tvdb/imdb are explicit source namespace labels and are corroborated by public provider links in HTML. Numeric values must not be treated as provider identity without the field namespace. uuid is opaque TV Time/source identity, not TMDB identity.

## Cross-file relationships

- 4 series list items carry tvdb_id; all 4 match a series id.tvdb in ENTRY-003.
- 3 movie list items carry uuid; 2 match movie UUIDs in ENTRY-002; 1 has no matching movie record in this archive.
- No list tvdb_id matches a movie. No list UUID matches a series.
- Series-to-season and season-to-episode relationships are represented by nesting. Seasons have no external ID; episodes have no explicit parent foreign keys.

The unmatched movie list link is a reviewable unresolved reference, not a deduplication instruction.

## Personal-state and rating findings

- Movies: 178 watched, 60 unwatched; rewatch_count is zero for all 238.
- Episodes: 6,234 watched, 16,432 unwatched; watched_count range 0–2; 2 episodes have watched_count > 1 and 2 have positive rewatch_count.
- Rewatch history is counters plus one watched_at, not repeated rows or a timestamp array.
- is_favorite exists for movies and series and is personal state.
- No rating/score field, rating scale, original-title field, episode air-date field, or series start-year field was observed.
- year exists only in the movie-like role; use as a medium-confidence match hint, not an asserted provider contract.

## Duplicate and conflict findings

No exact duplicate root record, duplicate provider ID, duplicate UUID, duplicate episode ID, duplicate JSON object key, or conflicting repeated ID was observed. This is absence in one sample, not a source guarantee. Future parsing must classify duplicates/conflicts and never silently deduplicate them.

## Evidence-backed field inventory

Required means present in every observed parent in this sample; it is not a claim about all exports.

| Source field/path | Role | Type | Required/optional | Blank/null behavior | Semantic/target | Confidence |
| --- | --- | --- | --- | --- | --- | --- |
| id | list root | string | required | non-blank | source/list identity -> ImportedSourceIdentity | high |
| name | list root or episode | string | required | non-blank | list or episode title -> review/media hint | high |
| description | list root | string | required | blank in sample | review-only text | medium |
| is_public | list root | boolean | required | boolean | list visibility, review-only | high |
| created_at | list/title/series root | string timestamp | required | non-null; precision varies | ImportedTimestamp(source creation) | medium |
| items | list root | array<object> | required | non-empty observed | list relationships | high |
| custom_order | list item | integer | required | 0–6 observed | source order/location | high |
| type | list item | string | required | movie/series observed | ImportedMediaHint.mediaType | high |
| tvdb_id | list item | integer | optional | absent 3/7 | qualified TVDB identity | high |
| uuid | list item/title root | UUID string | optional in list; required in title roots | absent only in list items | qualified TV_TIME identity | high |
| title | movie/series root | string | required | non-blank; Unicode/comma | ImportedMediaHint.title | high |
| year | movie-like root | integer | required | integer | likely release-year hint | medium |
| id.imdb | movie/series/episode | string or null | structurally required | movie string; series/episode null | qualified IMDb identity | high |
| id.tvdb | movie/series/episode | integer | required | integer | qualified TVDB identity | high |
| is_favorite | movie/series root | boolean | required | boolean | personal favorite/follow state | high |
| is_watched | movie/episode | boolean | required | boolean | ImportedWatchRecord.watchedState | high |
| watched_at | movie/episode | string or null | structurally required | null when unwatched; UTC seconds when watched | ImportedWatchRecord.watchedAt | high |
| rewatch_count | movie/episode | integer | required | zero or positive | ImportedWatchRecord.rewatchCount | medium |
| _noEpisodeData | series root | boolean | required | always false observed | technical warning metadata | low |
| status | series root | string | required | opaque string | review-only source status | medium |
| seasons | series root | array<object> | required | non-empty observed | episode context | high |
| number | season/episode | integer | required | zero and high values | season/episode number | high |
| is_specials | season | boolean | required | season zero true in observed specials | season special hint | high |
| episodes | season | array<object> | required | 3 empty arrays observed | season relationship | high |
| special | episode | boolean | required | boolean | episode special hint | high |
| watched_count | episode | integer | required | 0–2 observed | ImportedWatchRecord.watchCount | medium |

No source field supports ImportedRating, original title, episode air date, or direct TMDB identity. Do not invent defaults.

## Provider-neutral model refinement

The evidence supports these narrow Milestone 12B concepts:

- ImportedMediaHint: title; optional year; source role; qualified TVDB, IMDb, and opaque TV_TIME identities; review-only status.
- ImportedEpisodeHint: parent source series identity from nesting; season/episode numbers; episode title; special flags; episode TVDB identity; source location.
- ImportedWatchRecord: explicit watched boolean; nullable source timestamp; watch/rewatch counters; source location; warnings.
- ImportedRating: unsupported for this variant; no default value.
- ImportedTimestamp: original shape, normalized Instant only after the observed UTC-second form is parsed, source precision, and approximation marker.
- ImportedSourceIdentity: qualified namespace plus original lexical value; never a bare numeric ID.
- ImportSourceLocation: safe entry ID, record index, nested path; no absolute path or raw value.
- ImportWarning: unmatched list link, high season number, unknown field, duplicate/conflict, or invalid structure.

## Variant sufficiency

Covered: movies; TV series; multiple seasons; episodes; season zero; specials; watched/unwatched state; watched timestamps; repeated-watch counters; nullable values; Unicode; commas; quotes; empty episode arrays; large counts; cross-file links; auxiliary HTML.

Not covered: a second export/application version; second locale/date grammar; empty complete export; duplicate/conflict source records; movie rewatch_count above zero; ratings; original titles; air dates; watched records missing a timestamp; alternate archive layout/naming.

Conclusion: evidence is sufficient to specify and test a parser for the exact role-based JSON variant in tv-time-source-format-v1.md, but not to generalize to other TV Time variants.

## Milestone 12B implementation boundary

The production importer implements only this documented profile, under experimental wording. It requires one list array of length one, one movie-like array, and one series/episode array, with an optional auxiliary HTML entry. It detects roles from structure after bounded extension screening; private archive filenames are not a contract. Invalid safe records are reported with structural locations, while malformed JSON, duplicate keys, missing roles, duplicate roles, unknown roles, unsafe ZIPs, and unsupported layouts reject the source before matching. The importer does not read HTML as data and does not claim support for empty complete exports, CSV/TSV, other application versions/locales, or alternate archive layouts.

## Design boundary

The evidence study itself introduced no production parser or private sample dependency. Milestone 12B now implements the narrow boundary: bounded ZIP inspection, dedicated DTO parsing, provider-neutral hints, conservative TMDB matching, manual review/skip, and one confirmed additive Room transaction. No raw source JSON or URI enters persistent state; title-level missing `created_at` uses confirmation time only as an explicitly reported Library-membership approximation.

## Decision

Milestone 12A complete — evidence sufficient for Milestone 12B implementation
