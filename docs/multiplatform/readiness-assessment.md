# Multiplatform Readiness Assessment

## Executive Summary

This assessment evaluates the multiplatform readiness of Bingee prior to the `1.0.0-stable` release. The goal is to determine which parts of Bingee could realistically be shared with a future iOS or other client while preserving the current stable, local-first Android architecture.

**Decision Summary:** **Go/No-Go Decision: NO-GO for Kotlin Multiplatform (KMP) migration before `1.0.0-stable`.**
Bingee will preserve its single-module Android architecture. Backup v3 serves as the primary cross-platform interoperability contract. Re-evaluation will occur only after an approved iOS prototype exists and the Android `1.0.1` UI update is delivered.

---

## 1. Assessment Objectives & Scope

1. **Measure Portability**: Measure which current components are genuinely platform-neutral vs. Android-dependent.
2. **Identify Boundaries**: Distinguish necessary platform integrations (Room, Hilt, Compose, WorkManager, SAF) from domain logic.
3. **Verify Contract Stability**: Confirm that core domain models, identity schemes (`ExternalMediaRef`, `MediaSource`), and backup schema v3 are stable.
4. **KMP Evaluation**: Evaluate KMP feasibility without making premature source or build changes.
5. **Define Future Roadmap**: Outline a step-by-step extraction sequence, cost-benefit analysis, triggers, and rollback points for future multiplatform efforts.
6. **Protect Current App**: Ensure zero destabilization of the Android `1.0.0-stable` release candidate.

---

## 2. Codebase Inventory & Dependency Direction Audit

### Current Architecture Layering

```text
UI / Android Platform (Compose, ViewModels, Hilt, WorkManager, SAF)
                     ↓
        Application Use Cases / Coordinators
                     ↓
        Domain Contracts & Value Models (Pure Kotlin)
                     ↓
 Data Implementations & Adapters (Room DAOs, Retrofit, Keystore)
```

### Dependency Audit Findings

* **Pure Domain Layer (`com.cydoniancitizen.bingee.domain`, `com.cydoniancitizen.bingee.core.model`)**:
  * **Android/Framework Imports**: **0**. Zero imports of `android.*` or `androidx.*`.
  * **Persistence Annotations**: **0**. Zero Room annotations leak into domain models.
  * **Dependencies**: Uses pure Kotlin, `kotlinx.coroutines`, `kotlinx.coroutines.flow`, and standard library value types (`Instant`, `LocalDate`, `UUID`).
* **Backup & Interoperability Layer (`com.cydoniancitizen.bingee.data.importexport`)**:
  * **Codec (`BackupJsonCodec`, `BackupModels`, `BackupValidation`)**: **0** Android framework imports. Uses standard Kotlin and `kotlinx.serialization.json`.
  * **Gateway (`BackupFileGateway`, `BackupDataStore`)**: Android-dependent via SAF (`Uri`, `ContentResolver`) and Room (`withTransaction`).
* **Data Layer (`com.cydoniancitizen.bingee.data.*`)**:
  * Infrastructure-specific classes (Room entities, DAOs, Retrofit clients, EncryptedSharedPreferences) are properly isolated behind repository interfaces.

---

## 3. Provider & Identity Stability

### Provider-Qualified Identity
* All domain entities rely on `ExternalMediaRef(source: MediaSource, externalId: String)` or `MediaIdentity` rather than raw integer keys.
* Local Room IDs are strictly internal to `data/library/local` and never leak into domain interfaces or export files.

### Suspended Anime Provider Boundary
* Jikan DTOs, mappers, and repositories remain dormant and fully isolated.
* Multiplatform considerations do not rely on Jikan assumptions; backup v3 handles dormant Anime entries as valid portable records.

---

## 4. Backup v3 as Cross-Platform Interoperability Contract

Backup v3 (`docs/backup-format-v3.md` and `docs/backup/bingee-backup-v3.schema.json`) acts as the authoritative cross-platform data contract:

* **Portable**: Pure JSON format with explicit `$schemaVersion: 3`.
* **Provider-Independent**: Uses `MediaSource` and `externalId`.
* **Platform-Independent**: ISO-8601 UTC timestamps, primitive integers, and booleans.
* **Deterministic & Safe**: Validated via strict JSON Schema; transactional restore guarantees atomic application or complete rollback.
* **No Database Assumptions**: Independent of Room schema, SQL queries, or platform storage.

A future iOS app can parse, validate, and write backup v3 files natively without sharing Kotlin binary artifacts.

---

## 5. Go / No-Go Decision & Recommendation

### Decision: `Milestone 15 complete — no KMP migration justified before a concrete second client`

### Rationale:
1. **Zero Immediate ROI**: No iOS app or second client currently exists. Moving pure domain files to a KMP `shared` module adds build complexity and slows down Android iteration without producing a live second product.
2. **Clean Domain Already Exists**: The current domain layer has zero Android leakage. When an iOS project is approved, domain extraction can be executed cleanly in hours without architectural friction.
3. **Backup Interoperability Works Now**: Backup v3 allows data transfer between clients without shared binary modules.
4. **Android Release Priority**: Bingee `1.0.0-stable` must remain lean, stable, and easy to maintain.

---

## 6. Document Links

* [Component Portability Matrix](file:///C:/Users/thoma/Documents/Projects/Bingee/docs/multiplatform/component-portability-matrix.md)
* [Cost-Benefit Analysis](file:///C:/Users/thoma/Documents/Projects/Bingee/docs/multiplatform/cost-benefit-analysis.md)
* [Future Extraction Plan](file:///C:/Users/thoma/Documents/Projects/Bingee/docs/multiplatform/future-extraction-plan.md)
* [ADR 0023](file:///C:/Users/thoma/Documents/Projects/Bingee/docs/adr/0023-multiplatform-readiness-without-premature-android-migration.md)
