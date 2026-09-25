# ADR 0001: Adopt a local-first architecture

- Status: Accepted
- Date: 2026-07-31

## Context

Bingee promises that a user''s viewing history remains usable without an account, proprietary backend, or continued service availability. Core state must survive provider outages and loss of connectivity.

## Decision

Persist the library, progress, ratings, preferences, and release events locally. Main screens will observe local state through Room and `Flow`; remote providers refresh that state but do not become the synchronous source of truth for rendering.

Complete, versioned JSON export and transactional restore are product requirements. Backups must exclude credentials and device-specific data.

## Consequences

- Saved data remains readable offline and without an account.
- Provider errors cannot erase valid local state.
- Database migrations, backup validation, and restore atomicity are high-risk areas requiring dedicated tests.
- Cloud synchronization remains outside the Android MVP.
