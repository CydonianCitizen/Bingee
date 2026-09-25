# 23. Multiplatform Readiness Without Premature Android Migration

* Status: **Accepted**
* Date: 2026-08-06

## Context and Problem Statement

Bingee is approaching its `1.0.0-stable` release closure. As part of Milestone 15, an assessment was conducted to evaluate multiplatform readiness for potential future clients (e.g. iOS) while ensuring the current stable, local-first Android application is not destabilized or slowed down.

Should Bingee introduce Kotlin Multiplatform (KMP) modules or refactor its codebase prior to `1.0.0-stable`?

## Decision Drivers

1. **Android Application Stability**: Bingee `1.0.0-stable` must remain robust, bug-free, and easy to build and maintain.
2. **Local-First & Data Ownership**: User history belongs to the user and must remain accessible offline. Backup v3 is the primary data exchange contract.
3. **No Premature Abstraction**: No iOS application or second client is currently under active development.
4. **Clean Domain Separation**: Audits confirm that domain models (`core/model`) and contracts (`domain/repository`) have zero dependencies on Android APIs (`android.*`, `androidx.*`, Room, Compose).

## Considered Options

* **Option 1**: Retain current Android architecture without introducing KMP. Use Backup v3 as the cross-platform interoperability boundary. Re-evaluate shared-domain extraction after `1.0.1-stable` and upon approval of a concrete second client.
* **Option 2**: Extract a `:shared-domain` KMP module immediately prior to `1.0.0-stable`.
* **Option 3**: Full KMP migration replacing Room with SQLDelight and Jetpack Compose with Compose Multiplatform.

## Decision Outcome

Chosen Option: **Option 1 — No KMP migration before a concrete second client.**

### Positive Consequences

* **Zero Android Destabilization**: Android `1.0.0-stable` retains its proven single-module Gradle setup, fast build times, and zero framework overhead.
* **Complete Data Portability**: Backup v3 JSON schema acts as a platform-agnostic, versioned interoperability layer accessible by any future client.
* **Prepared for Future Extraction**: The domain layer's strict zero-Android-import boundary ensures that future extraction into KMP can occur smoothly when triggered.

### Negative Consequences

* Dual-platform business logic must be maintained separately if an iOS app is launched without a shared core (mitigated by clean zero-Android domain separation).

## Re-evaluation Triggers

This decision will be re-evaluated only if:
1. An official iOS client prototype is formally approved and funded.
2. Android `1.0.1-stable` UI update is completed and released.
3. Backup v3 manual file exchange proves insufficient for active dual-device users.
