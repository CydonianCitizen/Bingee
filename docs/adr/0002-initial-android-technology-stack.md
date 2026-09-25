# ADR 0002: Use the initial Android technology stack

- Status: Accepted
- Date: 2026-07-31

## Context

The Android foundation needs a stable, conventional stack that supports local persistence, provider access, background refresh, testability, and a Compose UI without speculative frameworks.

## Decision

Use Kotlin, Jetpack Compose, Material 3, Navigation Compose, Hilt, Room, Retrofit, OkHttp, WorkManager, coroutines, and `Flow`. Use AGP''s built-in Kotlin support, KSP for supported code generation, Kotlin DSL build scripts, and a Gradle version catalog.

Milestone 0 declares infrastructure dependencies but creates no database, API service, repository, worker, or DI module without a real binding.

Use Spotless with ktlint for reproducible Kotlin formatting. Android lint remains the Android-specific static-analysis gate.

## Consequences

- Contributors use mainstream Android tools and command-line Gradle checks.
- Build and dependency versions are centralized in `gradle/libs.versions.toml`.
- Generated-code compatibility must be checked when AGP, Kotlin, KSP, Hilt, or Room versions change.
- New libraries require a concrete need and a maintenance/license review.
