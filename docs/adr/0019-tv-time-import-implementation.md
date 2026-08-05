# ADR 0019: TV Time JSON ZIP import implementation

- Status: Accepted
- Date: 2026-08-05

## Context

Milestone 12A established one evidence-backed TV Time export profile, identified by `TVTIME-SAMPLE-001`. Bingee must import that profile without treating an untrusted archive, source identity, or TMDB result as authoritative. The existing Bingee backup restore has replacement semantics and must remain separate.

## Decision

Support only the documented role-based JSON ZIP profile. Android Storage Access Framework selects the archive; a bounded private cache copy permits central-directory inspection. Entry paths, sizes, compression ratio, duplicate/case-colliding paths, encryption, nested archives, and unsupported entry types are rejected before parsing. JSON roles are recognized from bounded structural signatures: exactly one list array, one movie-like array, and one series/episode array; one auxiliary HTML entry may be ignored. Private filenames are not part of the contract.

Dedicated source DTOs and a strict Gson-tree reader are separated from a provider-neutral import model. The reader rejects duplicate keys, invalid UTF-8, malformed structure, unsafe nesting, and invalid required fields. Safe record-level semantic errors are reported with structural locations; unsafe document-level corruption rejects the source. Unknown additive fields produce aggregate warnings and their values are not retained.

Matching reuses the existing authenticated TMDB clients. Qualified IMDb and TVDB IDs use TMDB's documented Find-by-external-ID endpoint only for compatible media types. Exact results must be unique and media-type compatible. Movies may receive a high-confidence proposal only when normalized title, exact year, and one compatible candidate agree. A series without an exact qualified identity remains manual, because the source profile has no verified series year. Regular watched episodes use accepted parent, season, and episode numbering; specials remain manual unless a qualified episode identity resolves uniquely. All ambiguous and unmatched records have explicit select/search/skip actions.

The official TMDB support matrix was rechecked on 2026-08-05. IMDb Find is supported for movies, TV shows, and episodes. TheTVDB Find is supported for TV shows, seasons, and episodes, but not movies. Therefore a source TVDB movie ID is provenance only: it is never sent through an unsupported TMDB movie Find route. Searches, details, seasons, episodes, authentication, and error handling use only documented TMDB v3 endpoints through Bingee's shared Retrofit/OkHttp stack.

The final plan is immutable, contains canonical TMDB metadata and no URI, source JSON, Retrofit DTO, Room ID, or unresolved candidate. After explicit preview confirmation, one Room transaction performs additive writes only. Existing membership timestamps, progress timestamps, ratings, notification state, refresh state, credentials, and delivery history win or remain unchanged. Source identities are stored in a separate provenance table so TVDB, IMDb, and TV Time UUID namespaces are not overloaded into `MediaSource`. Network and file I/O finish before the transaction. Repeating the plan is idempotent; any failure rolls back the entire transaction.

The feature is experimental and requires an existing TMDB credential for matching. It does not authenticate with or contact TV Time, upload the selected ZIP, import ratings/favorites/custom lists/rewatch timelines, claim CSV support, or claim compatibility with other TV Time variants.

## Consequences

The importer is conservative and may require manual review for many series and special episodes. The source profile and safety limits are auditable, and existing local history is protected. Additional TV Time formats require new evidence, documentation, fixtures, validation rules, and a deliberate profile decision; they must not be added through filename heuristics or a generic importer abstraction.

## Official references verified 2026-08-05

- TMDB: [Find by external ID](https://developer.themoviedb.org/reference/find-by-id), [movie search](https://developer.themoviedb.org/reference/search-movie), [TV search](https://developer.themoviedb.org/reference/search-tv), [movie details](https://developer.themoviedb.org/reference/movie-details), [TV details](https://developer.themoviedb.org/reference/tv-series-details), [season details](https://developer.themoviedb.org/reference/tv-season-details), [episode details](https://developer.themoviedb.org/reference/tv-episode-details), [application authentication](https://developer.themoviedb.org/docs/authentication-application), and [errors](https://developer.themoviedb.org/docs/errors).
- Android/Java: [OpenDocument](https://developer.android.com/reference/androidx/activity/result/contract/ActivityResultContracts.OpenDocument), [ContentResolver](https://developer.android.com/reference/android/content/ContentResolver), [ZipFile](https://developer.android.com/reference/java/util/zip/ZipFile), [Room transactions](https://developer.android.com/reference/androidx/room/Transaction), [Room database testing](https://developer.android.com/training/data-storage/room/testing-db), and [Compose accessibility](https://developer.android.com/develop/ui/compose/accessibility).

These references drove fixed-media-type search, transient `ContentResolver` use, private bounded ZIP inspection, one Room transaction, on-device Room tests, lazy review rendering, and explicit accessibility semantics. The archive API does not supply the import policy's decompression-bomb and path constraints; Bingee enforces those before parsing.
