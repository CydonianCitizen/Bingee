# Bingee

Bingee is an early-stage, open-source Android app for tracking films and TV series. Its core promise is local-first ownership: a user's history must remain usable without an account, a proprietary backend, or continued service availability.

## Project status

The project is unreleased and currently at **Milestone 0 — Repository Bootstrap**. The app contains only a minimal Compose shell with Home, Search, and Settings placeholders. Media search, library storage, TMDB access, background work, notifications, and import/export are planned but not implemented.

No API key is required at this milestone.

## Planned stack

- Kotlin with Jetpack Compose and Material 3
- Single-activity Navigation Compose
- Hilt dependency injection
- Room for local persistence
- Retrofit and OkHttp for provider access
- WorkManager for deferrable background refresh
- Coroutines and Flow as features are added
- JVM unit tests, with focused Android instrumentation tests where they add value

The checked-in Gradle Wrapper and version catalog are the source of truth for build and dependency versions.

## Prerequisites

- Git
- JDK 21
- Android Studio or Android command-line tools
- Android SDK Platform 36.1

Android Studio normally creates a local, ignored `local.properties` file that points to the Android SDK. Never commit this file.

## Build and verify

Clone the repository, then run these commands from its root:

```bash
./gradlew tasks
./gradlew spotlessCheck
./gradlew test
./gradlew lint
./gradlew assembleDebug
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

To apply Kotlin formatting:

```bash
./gradlew spotlessApply
```

The debug APK is generated under `app/build/outputs/apk/debug/`.

## Run

Open the repository in Android Studio, choose an emulator or connected device, and run the `app` configuration. From the command line, a configured device can install the debug build with:

```bash
./gradlew installDebug
```

The initial destination is Home. Bottom navigation exposes Home, Search, and Settings.

## Architecture

Bingee starts as a single Gradle application module and a lightweight modular monolith. Packages are grouped by responsibility:

```text
app/src/main/java/com/cydoniancitizen/bingee/
  core/       shared design-system and future infrastructure code
  data/       future source-specific and persistence implementations
  domain/     future business models and use cases
  feature/    feature UI
  ui/         application shell and top-level navigation
```

UI code will not access Retrofit services or Room DAOs directly. Provider DTOs, domain models, persistence models, and UI models will remain separate as their milestones arrive.

See [architecture decisions](docs/adr/) for durable project choices.

## Versioning

The project follows Semantic Versioning. While unreleased, development versions remain in the `0.y.z` range and may use a prerelease suffix. Milestone 0 uses `0.1.0-dev` with Android `versionCode` 1.

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md) and `AGENTS.md` before changing the project. Keep pull requests small, scoped, tested, and free of credentials or machine-local files.

Security reports follow [SECURITY.md](SECURITY.md).

## License

Bingee is licensed under the [Apache License 2.0](LICENSE).
