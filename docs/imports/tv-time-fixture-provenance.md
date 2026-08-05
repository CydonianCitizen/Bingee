# TV Time fixture provenance

Status: synthetic fixture permitted and safe for repository use.

Reviewed: 2026-08-04  
Source-profile: tv-time-source-format-v1  
Evidence reference: TVTIME-SAMPLE-001

## Permission boundary

The real ZIP was supplied for private local analysis only. Raw archive bytes, extracted files, personal values, source filenames, source identifiers, viewing history, ratings, and timestamps remain outside Git. Redistribution permission for the real export and for an anonymized derivative is not granted.

This fixture is not an anonymized copy. It is independently synthetic and preserves only verified structure:

- three JSON top-level arrays;
- list, movie-like, and series/episode roles;
- field names and primitive/container types;
- nullable watched_at behavior;
- list relationship patterns;
- canonical UUID and provider-ID lexical shapes;
- UTC-second watched timestamps;
- mixed created_at fractional precision;
- season zero, explicit specials, high season number, and empty episode arrays;
- Unicode, commas, and escaped quotes.

The HTML file is a tiny synthetic auxiliary report. It is not used as parser input.

## Fixture files

| File | Purpose |
| --- | --- |
| list.json | synthetic list object with 3 movie UUID links and 4 series TVDB links |
| movies.json | two synthetic movie-like records with watched and unwatched state |
| series.json | four synthetic series records with nested seasons and episodes |
| report.html | ignored synthetic presentation artifact |

All titles, identifiers, timestamps, counters, statuses, and list values were generated independently. No source row, title-history combination, source timestamp sequence, source filename, or source identifier was copied.

## Privacy scan requirements

Before commit or release, scan this directory and confirm:

- no email address;
- no account/profile/TV Time user ID;
- no authentication token, cookie, key, or password;
- no private URL, machine path, or source archive filename;
- no source timestamp;
- no source title-history combination;
- no raw evidence in Gradle, APK, AAB, or test reports.

Current local scan result: PASS. The fixture is under docs/imports, outside Android source sets and release resources.

## Limit

This fixture proves only the observed TVTIME-SAMPLE-001 role-based variant. It does not prove another TV Time application version, locale, archive layout, optional-field behavior, duplicate policy, rating format, or missing-watched-date case.

