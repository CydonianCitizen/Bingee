# TV Time sample intake

Status: private local intake complete for TVTIME-SAMPLE-001; no raw or anonymized derivative is committed.

Reviewed: 2026-08-04

## Current sample

- safe sample ID: TVTIME-SAMPLE-001;
- source class: user-supplied account export;
- inspection: permitted locally;
- raw retention: permitted only in the ignored private evidence area;
- anonymized derivative: not permitted;
- independent synthetic fixture: permitted after grammar verification;
- commit/redistribution of the real sample or a close derivative: not permitted;
- application and export-version metadata: unknown;
- personal-data risk: confirmed.

The raw archive and extracted files stay in '.local-evidence/tv-time/'. The archive filename and source values are intentionally excluded from repository documentation. Private digest and metadata live only in the ignored local analysis note.

## Accepted evidence

Preferred evidence is a complete export obtained through an account-controlled flow. A complete machine-readable example in official documentation may provide context, but prose, screenshots, and UI descriptions do not replace a file.

Acceptable local evidence classes:

- a real export supplied by its account owner for private analysis;
- a real export retained locally under documented inspection permission;
- an anonymized derivative whose creation and repository permissions are explicit;
- an official, complete machine-readable example whose reuse terms are known.

Not sufficient on its own:

- screenshots or manually transcribed tables;
- forum posts, blog descriptions, guessed headers, or remembered exports;
- a third-party parser fixture without provenance and a compatible license;
- a synthetic file not derived from verified source grammar;
- a partial excerpt that omits headers, metadata, or related files.

Do not fetch, upload, or submit account data automatically. Do not send sample content to TV Time, TMDB, a test service, or another external service as part of format analysis.

## Safe intake sequence

1. Obtain the export through intentional account-owner action.
2. Save the raw file outside the repository first.
3. Record provenance without names, emails, account IDs, tokens, cookies, or absolute paths.
4. Check completeness and machine readability without printing values.
5. If local analysis is permitted, retain it only under '.local-evidence/tv-time/'.
6. Inspect structure with bounded, value-redacting tooling.
7. Scan for personal data and credentials before any derivative.
8. Create only an independently synthetic fixture when close derivatives are not permitted.
9. Run private-data, credential, path, and artifact scans.
10. Keep raw evidence outside Git and release artifacts.

## Retention rules

- Raw private exports stay outside Git.
- No raw sample content belongs in logs, screenshots, crash reports, APKs, AABs, or test attachments.
- No sample is uploaded automatically.
- A local-analysis-only sample may be used by bounded local tooling, not committed.
- A repository fixture must be independently synthetic unless explicit derivative permission exists.
- If permission is unclear, treat it as absent.

## Exit

TVTIME-SAMPLE-001 establishes one role-based JSON source profile. Milestone 12B may be specified for that profile only. Additional export/application versions, locales, empty exports, and duplicate/conflict cases remain separate evidence work.

