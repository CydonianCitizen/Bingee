# Component Portability Matrix

This matrix categorizes all major components of Bingee according to their multiplatform reuse potential.

## Classifications

* **`SHARE_NOW`**: Pure Kotlin logic that is platform-neutral, stable, tested, and ready for sharing.
* **`SHARE_LATER`**: Potentially portable logic, but should remain in the Android app until a second client exists.
* **`ANDROID_ADAPTER`**: Platform integration code that must remain Android-specific.
* **`PROVIDER_ADAPTER`**: Remote provider integration with network DTOs and provider-specific error handling.
* **`DO_NOT_SHARE`**: Code whose sharing would create more complexity/coupling than value (e.g. Room DAOs, Compose UI).
* **`UNDECIDED`**: Requires an actual second client or additional evidence before deciding.

---

## 1. Component Inventory

| Component / Layer | Package / File Location | Responsibility | Current Dependencies | Android APIs Used | Classification | Future Extraction Recommendation |
| --- | --- | --- | --- | --- | --- | --- |
| **Media Identity Models** | `core/model/MediaIdentity.kt` | Identity types (`ExternalMediaRef`, `MediaSource`, `MediaType`) | Pure Kotlin | None | `SHARE_NOW` | Candidate for future `shared-domain` module |
| **Domain Entities & Value Objects** | `core/model/*` (`LibraryEntry`, `MediaDetails`, `Season`, `Episode`, `WatchProgress`, `ReleaseEvent`) | Core domain models | Pure Kotlin, `java.time` | None | `SHARE_NOW` | Candidate for future `shared-domain` module |
| **Media Link Models** | `core/model/MediaLinkModels.kt` | Equivalence & link entities | Pure Kotlin | None | `SHARE_NOW` | Candidate for future `shared-domain` module |
| **App Result & Error Models** | `core/result/AppResult.kt`, `AppError.kt` | Error classification & result wrappers | Pure Kotlin | None | `SHARE_NOW` | Candidate for future `shared-domain` module |
| **Backup Models & Validation** | `data/importexport/BackupModels.kt`, `BackupValidation.kt` | Portable JSON backup schema & rules | `kotlinx.serialization` | None | `SHARE_NOW` | Shared codec candidate |
| **Backup JSON Codec** | `data/importexport/BackupJsonCodec.kt` | Serializes/deserializes backup v3 | `kotlinx.serialization.json` | None | `SHARE_NOW` | Shared codec candidate |
| **Calendar Refresh Coordinator** | `domain/calendar/DefaultCalendarRefreshCoordinator.kt` | Refresh planning & release calculation | Domain repositories, Flow | None | `SHARE_LATER` | Keep in Android until iOS architecture aligns |
| **Equivalence Domain Use Cases** | `domain/equivalence/*` | Candidates & link resolution logic | Domain repositories | None | `SHARE_LATER` | Extract when cross-platform link engine is needed |
| **Repository Contracts** | `domain/repository/*` | Abstractions for data access | Domain models, `Flow` | None | `SHARE_LATER` | Shared interfaces if KMP is adopted |
| **TMDB Provider Client & Mappers** | `data/tmdb/*` | Network DTOs, mappers, Retrofit services | Retrofit, OkHttp, Moshi/Gson | None (network only) | `PROVIDER_ADAPTER` | Shared networking optional via Ktor in distant future |
| **Jikan Anime Provider Client** | `data/jikan/*` | Dormant Anime API DTOs and mappers | Retrofit, OkHttp | None | `PROVIDER_ADAPTER` | Keep isolated behind domain interface |
| **Room Database & DAOs** | `data/library/local/*` | Local persistence & Room migrations | Room, SQLite | `android.database`, Room | `DO_NOT_SHARE` | Android-specific storage. iOS uses SwiftData/SQLite |
| **Encrypted Credential Storage** | `data/settings/EncryptedCredentialStore.kt` | TMDB API Key storage | EncryptedSharedPreferences | `security-crypto`, Android Keystore | `ANDROID_ADAPTER` | Android integration. iOS uses Keychain |
| **Backup File Gateway** | `data/importexport/BackupFileGateway.kt` | File IO via SAF | ContentResolver, Uri | `android.net.Uri`, SAF | `ANDROID_ADAPTER` | Android integration. iOS uses DocumentPicker |
| **WorkManager Workers** | `data/worker/*` | Periodic background refresh & notifications | WorkManager, Hilt Worker | `androidx.work.*` | `ANDROID_ADAPTER` | Android background job. iOS uses BackgroundTasks |
| **Local Notification Engine** | `core/notification/*` | System notifications & notification channel | NotificationManager | `android.app.Notification` | `ANDROID_ADAPTER` | Android notification engine. iOS uses UNUserNotificationCenter |
| **ViewModels & UiStates** | `feature/*/*ViewModel.kt` | Screen state holders | `lifecycle-viewmodel-ktx`, `SavedStateHandle` | AndroidX Lifecycle | `SHARE_LATER` | Keep ViewModels in native UI layers |
| **Jetpack Compose UI** | `feature/*/*.kt`, `core/designsystem/*` | UI screens and Material 3 components | Compose M3, Navigation Compose | Compose Runtime/UI | `DO_NOT_SHARE` | Android native UI. iOS uses native SwiftUI |
| **Hilt Dependency Injection** | `di/*` | DI bindings | Hilt, Dagger | Android Hilt | `ANDROID_ADAPTER` | Android DI. KMP can use Koin or Swift DI |

---

## 2. Accidental vs. Essential Coupling Analysis

* **Accidental Dependencies**: **0 found in domain**. The audit confirmed zero Android imports in `com.cydoniancitizen.bingee.domain` and `com.cydoniancitizen.bingee.core.model`.
* **Essential Dependencies**: Room, WorkManager, SAF, Android Keystore, and Jetpack Compose are essential Android framework capabilities. They remain in their respective adapter modules.
