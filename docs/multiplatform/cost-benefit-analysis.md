# Multiplatform Cost-Benefit Analysis

This document provides a comparative cost/benefit analysis of multiplatform strategies for Bingee.

---

## 1. Multiplatform Strategy Comparison

| Strategy Option | Relative Effort | Reusable Code % | Android Regression Risk | iOS Development Benefit | Maintenance Burden | Recommendation |
| --- | --- | --- | --- | --- | --- | --- |
| **Option A: Backup v3 Interoperability + Platform-Native Apps** | **Small** | ~15% (Data contract & Backup JSON schema) | **None (0%)** | Medium (Independent Swift development using standard iOS tooling) | **Low** (Zero KMP build overhead) | **RECOMMENDED FOR 1.0.0** |
| **Option B: Shared Domain & Backup Codec (`:shared-domain` KMP)** | **Medium** | ~35% (Domain models, progress rules, backup codec) | **Low** | High (Guarantees business logic parity) | **Medium** (Requires KMP Gradle tooling & Swift interop management) | **Recommended for post-1.0.1 (if iOS approved)** |
| **Option C: Shared Domain + Shared Networking (Ktor)** | **Medium-Large** | ~50% (Domain + TMDB/Jikan API clients) | **Medium** | High (Single network layer) | **Medium-High** (Replacing Retrofit/OkHttp on Android) | **Defer until second client matures** |
| **Option D: Shared Persistence (SQLDelight + KMP)** | **Large** | ~75% (Domain + DB logic) | **High** (Requires rewriting Room v1 database) | Very High (Shared DB schema) | **High** (High migration and ORM risk on Android) | **REJECTED** |
| **Option E: KMP Domain/Data + Android Native UI (Compose)** | **Medium** | ~50-60% (Domain + Non-DB Data) | **Low** (Preserves Room, UI, Navigation, standard Android state) | High (Shared domain logic) | **Low** (Android app remains pure Android native) | **PREFERRED ARCHITECTURE** |

---

## 2. Rejection Rationale for Options D & E

1. **Rejection of SQLDelight / Shared DB (Option D)**: Room database schema (v1 initial stable schema), FTS, and transactional DAOs is highly optimized and thoroughly tested. Replacing Room with SQLDelight would introduce massive regression risk for zero benefit to current Android users.
2. **Rejection of Compose Multiplatform (Option E)**: Jetpack Compose on iOS does not deliver the native HIG polish required for an open-source client. Native SwiftUI provides superior iOS accessibility, performance, and user experience.
