# ADR 0005: Define domain boundaries, contracts, and external identity

- Status: Accepted
- Date: 2026-08-01

## Context

Provider and persistence work needs stable domain language before TMDB DTOs or Room entities exist. IDs from different providers can have the same text, while anime is a content category rather than a distinct structural shape.

## Decision

Keep one Gradle application module and enforce package dependency direction. Plain Kotlin models and results live in `core`; repository contracts live in `domain/repository`; feature and shared UI depend inward on them. UI and domain never expose provider DTOs, DAOs, HTTP responses, or Room entities.

Use `ExternalMediaRef(MediaSource, String)`. The non-blank string preserves provider-specific identity and prevents cross-provider collisions. Support `TMDB` and `JIKAN` as identity sources without implementing either provider.

Represent structural media as `MOVIE` or `SERIES`. Do not model anime as a third structure. Keep search summaries separate from details, metadata separate from `LibraryEntry` personal state, season zero valid, and release timing explicit as date-only or precise `Instant`.

Define only `MediaRepository` and `LibraryRepository`. Remote one-shot reads and local writes return `AppResult`; observable local library state uses `Flow`.

## Consequences

- Domain models remain independent of Android UI, Room, Retrofit, and Compose.
- Equal provider IDs from different sources remain distinct.
- Provider clients and persistence implementations can change behind contracts.
- Current model fields and repository signatures are provisional until real integrations exercise them.
- Local database IDs, provider DTOs, progress rules, provider deduplication, and extra repositories remain deferred.
