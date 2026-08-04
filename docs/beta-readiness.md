# Bingee beta readiness

Status: `Milestone 11 partially complete` as of 2026-08-04.

This document is the reproducible gate for the first Android beta. It records evidence, not intended behavior. A beta is not public-ready while the device-only blockers below remain open.

## Scope and supported behavior

Bingee is an Android, local-first movie and TV-series tracker. The current vertical slice supports:

- TMDB movie and TV search and details;
- local Library add/remove, filters, and sorting;
- cached seasons, season zero, and episodes;
- movie and episode watched state;
- title-level 1–10 ratings;
- a local release calendar;
- approximate WorkManager refresh and optional local notifications;
- versioned plaintext JSON export and replace-only restore through SAF.

No Bingee account, proprietary backend, cloud sync, analytics, crash-reporting SDK, Jikan behavior, or TV Time import exists.

The tested build is `0.1.0-dev` / version code `1`. The intended first beta release-note label is `0.1.0-beta1`; signing and publication remain maintainer actions.

## Platform and credential requirements

- Minimum and target SDK are defined by the checked-in Gradle configuration; the current build baseline is Android SDK 36.1 and JDK 21.
- The completed connected evidence uses a `Pixel_7` AVD, API 33 / Android 13, 1080×2400, density 420, `en-US`, font scale 1.0, gesture navigation, animations enabled.
- No API 36 runtime was available in this run. Do not infer API 36 behavior from the API 33 result.
- A user-supplied, TMDB-accepted Read Access Token is needed for remote search and refresh. Offline continuation remains available without it.

## Privacy and backup warning

Room, preferences, watch history, ratings, cached metadata, and selected notification categories are local. The credential is encrypted, stored separately under Android's no-backup directory, and is never exported. JSON backups are plaintext and not password protected; they can contain titles, membership, watched timestamps, ratings, seasons/episodes, and portable notification choices. Notification permission, device enablement, internal IDs, delivery ledger, refresh state, WorkManager state, URI, and credential remain device/technical state.

Restore is replace-only and explicit. Users must retain a backup before uninstalling or changing devices. A malformed or failed restore must not alter the existing database.

Background notifications are approximate. Android may delay WorkManager because of Doze, battery policy, constraints, or device state; Bingee makes no exact-time promise.

## Evidence completed

Static and JVM gates passed during this milestone: Spotless, JVM tests, lint, debug build, release build, debug instrumentation compilation, schema v7 generation, diff check, release secret/path scans, and focused runtime suites.

API 33 focused runtime evidence passed:

- direct and full Room migration tests (8 tests);
- Room restore replacement, repeated restore, all ten failure-injection stages, and semantic export/restore round trip (3 tests);
- FileProvider path, `content://` read, read-only share intent, stale-file cleanup, and gateway parsing (4 tests);
- detail accessibility semantics (6 tests);
- notification settings roles/selection semantics (3 tests);
- notification cold/warm/malformed navigation (3 tests);
- notification channel/content platform behavior (2 tests).

The final connected API 33 suite passed: 106 tests completed, 0 skipped, 0 failed.

Final release inspection found only the intended Internet, notification, WorkManager support, and internal dynamic-receiver permissions; no foreground-service or broad-storage permission remains. The merged release manifest has a non-exported, cache-only FileProvider, and the release dependency/artifact scans found no test libraries, logging interceptor, analytics, crash-reporting, or cloud SDK. The only APK name match was Kotlin coroutine debug-probe metadata (DebugProbesKt.bin), not a debug dependency or debug code path.

A clean synthetic startup smoke on the same emulator measured cold TotalTime=5045 ms after test-app data clear and an already-running warm delivery of WaitTime=14 ms. A five-frame gfxinfo sample contained three janky frames, too small and emulator-specific to justify a product performance change. No benchmark claim is made from it.

## Open verification gates

These are not claims of failure in product code; they are evidence still required before public beta:

- real CreateDocument/OpenDocument picker flows, including cancellation and interrupted streams;
- real Sharesheet target launch and a manual FileProvider read grant check;
- uninstall/reinstall recovery using a synthetic backup;
- TalkBack walkthrough and Switch Access/keyboard walkthrough;
- 100/130/160/200% font-scale and narrow-screen manual passes;
- an API 36 runtime pass;
- process-death restoration. The API 33 ActivityScenario runner could not reliably recreate this Compose activity, so the attempted probe is recorded as a harness limitation rather than a pass;
- measured startup/scroll/export/restore profiling and 1,000-title synthetic stress run.

Until these are executed and recorded, the milestone remains partial and is not public-beta ready.

## Manual beta checklist

Use only synthetic data. Record device/API/density/resolution/locale/font scale/navigation mode/network and notification permission state.

1. Fresh install: launch with no credential; verify no permission prompt, no crash, offline continuation, and usable Home/Library/Settings.
2. Configure a test TMDB token, search one movie and one series, add/remove Library entries, open details, expand season zero and a regular season, mark movie/episode watched, and set/remove a rating.
3. Disable network: verify startup, Home, Library search/filter/sort, cached details/episodes, progress, ratings, export, restore, and cached notification evaluation. Verify uncached search/details/refresh fail safely.
4. Remove the credential: verify all local state, cached details, events, export, restore, and cached notification evaluation remain; remote operations request configuration and no personal data is deleted.
5. Notifications: start disabled; explicitly enable; test grant, denial/system block, channel, lead/category changes, due event, duplicate suppression, private lock screen, cold/warm tap, and disable cancellation. Do not assert exact delivery time.
6. Backup: CreateDocument with the suggested JSON filename, save, cancel, import valid/malformed/wrong-version/oversized files, cancel, preview, replace, repeat restore, share, and inspect JSON for no credential/URI/device ID.
7. Recovery: export, uninstall, reinstall, launch without credential, choose offline continuation, restore, and verify Library, ratings, progress, seasons/episodes, events, and portable notification choices. Verify credential and device notification enablement are absent.
8. Accessibility: TalkBack every critical flow; check headings, roles, state descriptions, focus, touch targets, 200% text, narrow screen, light/dark contrast, keyboard/switch navigation, dialogs, errors, and decorative image silence.
9. Release/privacy: inspect release manifest and APK/AAB, logcat, permissions, FileProvider paths, PendingIntents, dependency graph, backup rules, and scans.

## Rollback strategy

Do not distribute a beta with a known data-loss, restore, migration, startup-crash, or credential-leak issue. If a beta is broken, stop distribution, publish a concise incident note, advise users not to uninstall and to preserve their JSON backup, and ship a corrected signed build with a higher version code. Never ask users to delete app data as a first recovery step.

## Issue reports

Report app version, Android/API level, device, locale/font scale, network and notification state, exact steps, expected/observed result, and a redacted log excerpt. Use synthetic titles and fake credentials. Never attach a real token, raw backup, URI, private path, device identifier, or unredacted logcat.

## Release-blocking criteria

Block public beta for any migration failure, incomplete restore rollback, semantic round-trip loss, unsafe URI/path exposure, credential export/logging, invalid release artifact, critical TalkBack or 200% layout defect, startup crash, known data loss, or unverified required device flow. A closed beta may proceed only after maintainers explicitly accept the documented manual gaps and signing status.

## Current known limitations

- background work and notifications are approximate, not exact alarms;
- remote metadata requires the user's TMDB credential;
- no cloud synchronization or account exists;
- Jikan/anime is not implemented;
- TV Time and generic third-party imports are not implemented;
- JSON backup is plaintext;
- release signing is not configured in the repository;
- release optimization/minification is disabled in the current development build;
- API 36, TalkBack, real SAF picker, reinstall recovery, and process-death evidence are pending for this run.
