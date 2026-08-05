# TV Time import implementation verification

Status: Milestone 12B implementation evidence, updated 2026-08-05.

## Official documentation check

Primary sources and affected decisions are recorded in [ADR 0019](../adr/0019-tv-time-import-implementation.md). Most importantly, TMDB's external-ID matrix excludes TheTVDB IDs for movies, so Bingee never treats the observed movie `id.tvdb` as a supported TMDB Find route. `OpenDocument` and `ContentResolver` keep selection permissionless and transient; `ZipFile` supplies random-access inspection while Bingee supplies stricter path, size, count, ratio, nesting, duplicate, and cleanup policy; Room contains the additive commit in one transaction; Compose uses lazy groups, non-color confidence text, state descriptions, and scroll-safe large-font layouts.

## Initial Ponytail whole-repository audit

Procedure: read-only inspection of the production tree, tests, Room schemas and migrations, backup/SAF boundaries, matching clients, Settings route, source sets, logs, and repository evidence boundaries. Findings were classified before changes.

Must fix in 12B:

- remove unsupported TVDB-to-movie exact lookup;
- move stale-cache cleanup off gateway construction/main thread;
- distinguish malformed UTF-8/JSON and preserve cancellation;
- bound individual JSON records and validate duplicate season/episode numbering;
- avoid retaining unsupported raw series status;
- guard preview-to-commit state against stale local changes;
- add grouped episode review and season skip;
- strengthen ZIP, parser, matching, rollback, idempotency, and state-independence tests.

Intentionally retained:

- focused TV Time ZIP, parser, TMDB matching, and Room transaction seams;
- dedicated source DTOs as the exact verified-profile contract;
- sequential matching as a conservative concurrency bound;
- Room v8 provenance because `MediaSource` describes canonical metadata providers, not IMDb/TVDB/TV Time provenance;
- Bingee backup restore as a separate replace-only flow.

Valid outside scope: a future Settings navigation redesign and broader import-provider abstractions. Rejected: reusing replacement-backup parsing/transaction code, generic multi-provider scaffolding, or removing conservative manual review. False positives: the small focused gateway interfaces are test/security boundaries, not speculative framework layers.

## Safety and semantic split

Archive/layout corruption, duplicate JSON keys, invalid UTF-8, unknown roles, unsafe paths, nested archives, size/count/ratio breaches, conflicting identity ownership, and structurally unsafe relationships reject the document before matching. Safely bounded record-level semantic errors are retained only as structural invalid-record diagnostics; they cannot be accepted. Unknown additive fields are summarized by safe structural location, field name, and occurrence count; values are discarded.

## Verification boundary

Only synthetic fixtures and generated test ZIPs are used. `.local-evidence/` and raw archives remain outside tracked files and build source sets. Runtime device evidence, if unavailable, remains open and does not resolve Milestone 11's beta blockers.

## Final Ponytail maintenance pass

The complete production tree and diff were reviewed after implementation. Removed: an unused supported-profile display constant, duplicate unused review-warning fields, and a one-property progress-result wrapper. Candidate warnings now use the provider-neutral source warnings directly. Retained deliberately: source DTOs for the exact verified schema, focused testable gateway interfaces, the logical transaction failure injector used only to prove rollback stages, the provenance migration, and the conservative sequential request lane. No generic import framework, alternate-format branch, unused import state, duplicate normalization/scoring path, or extra dependency was added. Further splitting of the strict parser was deferred because it would add coordination layers without reducing the profile's validation rules.
