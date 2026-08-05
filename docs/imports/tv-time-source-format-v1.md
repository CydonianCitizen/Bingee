# TV Time source format profile v1

Status: supported-source design contract only; no production parser is implemented.

Reviewed: 2026-08-04  
Evidence: TVTIME-SAMPLE-001 (private local evidence)

The suffix v1 names Bingee's supported source-profile version. It is not an official TV Time export-version claim.

## Supported input

The supported profile is a ZIP containing exactly one unambiguous role instance for each of these machine-readable JSON roles:

1. list role;
2. movie-like title role;
3. series/episode role.

An auxiliary HTML/report entry may be present and is ignored. The original archive's private entry names are intentionally redacted from repository documentation. Role selection must use bounded extension plus top-level shape, not a filename copied from private evidence.

The parser must reject:

- missing or ambiguous role instances;
- more than one candidate JSON for a role;
- JSON Lines, CSV, TSV, XML, SQLite, or a different top-level shape;
- encrypted or unsafe ZIPs;
- malformed JSON, duplicate JSON object keys, unexpected required-field types, or path traversal;
- unsupported source variants rather than guessing.

The parser may ignore the HTML role only after verifying it is not the selected machine-readable source. It must not use HTML values to fill JSON fields.

## Archive safety contract

Apply the existing local evidence limits before reading entries:

- archive at most 200 MiB;
- total decompressed content at most 1 GiB;
- at most 100,000 entries;
- individual entry at most 500 MiB;
- compression ratio at most 100:1;
- no absolute, drive, UNC, traversal, null-byte, duplicate-normalized, or case-colliding paths;
- no encrypted or symbolic-link-like entries;
- nested archives are unsupported.

Extraction must be into a private bounded temporary root. Do not follow links, overwrite files, or mutate Room before full parse and user confirmation.

## Text contract

Observed source grammar:

- UTF-8 without BOM;
- LF line endings;
- ordinary JSON, not JSON Lines;
- top-level array in all three selected JSON roles;
- standard JSON string quoting/escaping;
- no duplicate object keys in the supplied sample.

The parser must preserve original source text only in bounded review data, not logs. It must not normalize line endings or rewrite source bytes before validation.

## Role grammar

### List role

Top-level array length one. Sole object fields:

- id: string;
- name: string;
- description: string;
- is_public: boolean;
- created_at: timestamp string;
- items: array.

Each item:

- custom_order: integer;
- name: string;
- type: string, observed movie or series;
- tvdb_id: optional integer;
- uuid: optional canonical UUID string.

Observed list: 7 items; 4 series items with tvdb_id; 3 movie items with uuid. Description may be blank.

### Movie-like role

Top-level array of title objects. Fields:

- created_at: timestamp string;
- id.imdb: string matching observed tt plus digits;
- id.tvdb: integer;
- is_favorite: boolean;
- is_watched: boolean;
- rewatch_count: integer;
- title: string;
- uuid: canonical UUID string;
- watched_at: timestamp string or null;
- year: integer.

The source does not emit a record-level media-type field. Role itself is the media-type hint. Do not silently reinterpret a movie-role record as a series.

### Series/episode role

Top-level array of series objects. Series fields:

- _noEpisodeData: boolean;
- created_at: timestamp string;
- id.imdb: null;
- id.tvdb: integer;
- is_favorite: boolean;
- seasons: array;
- status: string;
- title: string;
- uuid: canonical UUID string.

Season fields:

- episodes: array;
- is_specials: boolean;
- number: integer.

Episode fields:

- id.imdb: null;
- id.tvdb: integer;
- is_watched: boolean;
- name: string;
- number: integer;
- rewatch_count: integer;
- special: boolean;
- watched_at: timestamp string or null;
- watched_count: integer.

Season zero, special flags, empty episode arrays, and season numbers at or above 1000 are valid observed values. Do not clamp, remap, or infer numbering.

## Timestamp contract

Watched timestamps use exactly the observed second-precision UTC form:

~~~
YYYY-MM-DDTHH:mm:ssZ
~~~

Watched values are present when the observed is_watched value is true and null when false. Preserve absent/null distinctly from an exact timestamp. Do not replace a null with import time.

created_at also ends in Z but may contain no fraction or four-, five-, or six-digit fractional seconds. Treat it as source metadata, never as watch history.

No rating timestamp or episode air-date field exists in this profile.

## Identity and relationships

Namespaces:

- TVDB: id.tvdb or list tvdb_id, JSON integer;
- IMDb: id.imdb, JSON string or null;
- TV_TIME: uuid, opaque canonical UUID;
- no direct TMDB identifier observed.

Series identity is the series TVDB ID or source UUID. Season identity is parent series plus season number; no season external ID is emitted. Episode identity is parent series plus season context plus episode TVDB ID; episode number remains a required corroborating field.

List links:

- series list item: TVDB ID;
- movie list item: source UUID;
- unmatched list links remain warnings.

Do not use a bare numeric value as a global ID. Do not merge TVDB and IMDb values. Do not infer TMDB equality from title alone.

## Normalization rules

Preserve both source and normalized forms in the intermediate model:

- trim only for comparison; retain original strings;
- Unicode comparison normalization must be documented and locale-safe;
- case folding is comparison-only;
- do not remove punctuation or rewrite apostrophes/quotes;
- parse integers only from JSON number tokens;
- preserve season zero and high season numbers;
- parse watched_at only in the verified UTC-second form;
- null remains unknown/null;
- unknown fields are retained as review warnings, not dropped silently;
- source location is safe entry ID plus record/object path, never an absolute machine path.

## Provider-neutral mapping

- movie/series title -> ImportedMediaHint.title;
- movie year -> ImportedMediaHint.year, medium-confidence hint;
- type or role -> ImportedMediaHint.mediaType;
- TVDB/IMDb/TV_TIME IDs -> qualified ImportedSourceIdentity;
- nested series/season/episode context -> ImportedEpisodeHint;
- is_watched and watched_at -> ImportedWatchRecord;
- rewatch_count and watched_count -> counters with source semantics;
- special/is_specials -> explicit special hints;
- list order -> ImportSourceLocation/order;
- description/status/_noEpisodeData -> review-only metadata;
- rating -> unsupported, because no rating field is observed.

No field is available for original title, episode air date, direct TMDB ID, or rating.

## Duplicate and error behavior

The source sample had no duplicates or conflicts. The future parser must still:

- detect duplicate object keys;
- detect repeated root IDs and repeated episode IDs;
- detect same ID with conflicting title/year/context;
- preserve first-class warnings and source location;
- never silently choose a winner;
- allow review or skip.

Malformed or structurally invalid records are rejected individually only under an explicit policy; the archive-level validation result must remain visible. No database write occurs before the complete preview is confirmed.

## Unsupported and unresolved behavior

This profile does not claim support for:

- other archive names/layouts;
- CSV/TSV/JSONL exports;
- localized date strings;
- missing watched_at on a watched record;
- ratings;
- original titles;
- air dates;
- duplicate precedence;
- alternate application versions or locales;
- repeated-watch timestamp arrays.

Additional evidence should create a new source profile or expand this one with verified tests. It must not broaden v1 by assumption.

## Privacy and fixture policy

Raw sample and extracted files remain in the ignored private evidence area. No anonymized derivative is committed. Committed fixtures are independently synthetic and document only this verified grammar. Redistribution permission for the private source remains absent.

## Milestone 12B implementation status

The experimental importer now implements this exact contract only. It validates the archive and role grammar before matching, keeps malformed safe records in the invalid summary, requires review or skip for unresolved records, and writes only an explicitly confirmed additive plan. The list creation timestamp remains structurally required; when a title-level `created_at` is absent from a semantically safe record, the importer uses confirmation time for new Library membership and reports that approximation. A watched record still requires its verified `watched_at`.
