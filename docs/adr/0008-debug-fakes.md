# ADR 0008: Keep deterministic architecture fakes in the debug source set

- Status: Accepted
- Date: 2026-08-01

## Context

Previews and JVM tests need repository behavior before real providers or persistence exist. Fake provider behavior must not enter release builds, and duplicate fake implementations would drift.

## Decision

Place deterministic repository fakes, fixed fixtures, the presentation sample, and its ViewModel in `app/src/debug`. Debug-variant JVM tests in `app/src/test` reuse these classes. Production navigation never references the sample, and release compilation excludes it.

Fakes return immediately, support configured success or failure, and use fixed IDs, titles, dates, and timestamps. They do not sleep. No current fake reads the current time; a future time-dependent fake must accept `java.time.Clock`.

## Consequences

- Previews and tests share one deterministic fake implementation.
- Release artifacts cannot accidentally provide fake repository behavior.
- The sample demonstrates repository to ViewModel to immutable UI state without becoming a partial production feature.
- The sample state and fixtures are provisional development aids.
- A separate fixtures module or test-fixtures plugin remains deferred until multiple modules or consumers justify it.
