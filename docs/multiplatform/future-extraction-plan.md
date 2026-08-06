# Future Multiplatform Extraction Plan

This document outlines the step-by-step extraction sequence, triggers, rollback points, and testing strategy for a future multiplatform Bingee client.

---

## 1. Trigger Conditions for Reconsideration

No Kotlin Multiplatform (KMP) refactoring or code extraction should begin unless **at least two** of the following conditions are met:

1. **Approved Second Client**: An official iOS prototype or second-client project is formally approved.
2. **Stable `1.0.1-stable` Android Release**: Android `1.0.0-stable` and `1.0.1-stable` (UI refresh) are fully shipped.
3. **Active Multiplatform Maintainer**: A dedicated maintainer for Swift/iOS integration is assigned.
4. **Proven Interoperability Pain**: Backup v3 cross-platform manual syncing proves insufficient for dual-device users.

---

## 2. Safe Future Extraction Sequence

If KMP migration is triggered in the future, follow this strict 11-phase sequence:

```text
Phase 1: Maintain Android stability & suite
   ↓
Phase 2: Formalize iOS project & backup v3 parser in Swift
   ↓
Phase 3: Create isolated `:shared-domain` KMP module (no Android dependencies)
   ↓
Phase 4: Move pure value objects & models (`core/model`) to `:shared-domain`
   ↓
Phase 5: Move backup codec (`BackupJsonCodec`, `BackupModels`) to `:shared-domain`
   ↓
Phase 6: Move domain business rules & calculation engines to `:shared-domain`
   ↓
Phase 7: Retain Room on Android as `:androidApp` adapter; implement SwiftData / SQLite on iOS
   ↓
Phase 8: Share test fixtures & behavioral repository contract test suites
   ↓
Phase 9: (Optional) Share Ktor networking if provider client unification is desired
   ↓
Phase 10: (Optional) Evaluate shared ViewModels or stay with native SwiftUI
   ↓
Phase 11: Final integration & cross-platform validation
```

---

## 3. Rollback Points

Every extraction phase must include an explicit rollback plan:

* **Phases 3–6 Rollback**: If KMP gradle setup destabilizes Android build times or AGP compatibility, delete `:shared-domain` and restore Kotlin files into `app/src/main/java`. Since models are identical pure Kotlin, source-level rollback is trivial.
* **Phase 7 Rollback**: If shared persistence abstraction degrades Room performance or complicates migration scripts, abandon shared persistence and maintain platform-native database engines (Room on Android, SwiftData/SQLite on iOS) connected via Backup v3.

---

## 4. Test Strategy for Future Shared Core

When `:shared-domain` is created:

1. **Common Pure Unit Tests**: Move `BackupJsonCodecTest`, `BackupValidatorTest`, and domain logic tests to `commonTest`.
2. **Contract Fixtures**: Shared JSON backup fixtures (`valid-linked-v3.json`, `valid-numeric-collision-v3.json`) must be checked by both JVM/Android and Native/iOS test targets.
3. **Deterministic Clocks & Mocking**: Tests must use injectable fixed clocks (`Instant`) and zero real network calls.
4. **Behavioral Repository Tests**: Define abstract test suites in `commonTest` that verify both Room (Android) and SwiftData/SQLite (iOS) data access adapters satisfy identical behavioral invariants.
