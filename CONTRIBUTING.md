# Contributing to Bingee

Thanks for helping build Bingee. The project is early-stage, so small and well-tested changes are especially valuable.

## Prepare the environment

1. Read `AGENTS.md`, this guide, and the relevant ADRs in `docs/adr/`.
2. Install JDK 21 and Android SDK Platform 36.1.
3. Open the project in Android Studio or configure the Android SDK path locally.
4. Confirm the checked-in wrapper works with `./gradlew tasks`.

Use `gradlew.bat` instead of `./gradlew` on Windows.

## Branches and pull requests

- Create a focused branch from the current default branch.
- Keep each pull request small and limited to one coherent change.
- Explain behavior changes, architecture decisions, tests, and known limitations.
- Link the relevant issue when one exists.
- Do not mix broad refactors, dependency upgrades, or cosmetic rewrites into feature work.
- Do not commit generated build output, IDE-local state, signing material, API keys, tokens, or user data.
- Do not add a dependency without a concrete need and a license/maintenance review.

Maintainers may request an ADR for structural decisions that are difficult to reverse.

## Required local checks

Before opening a pull request, run:

```bash
./gradlew spotlessCheck
./gradlew test
./gradlew lint
./gradlew assembleDebug
```

Use `./gradlew spotlessApply` to format Kotlin and Kotlin Gradle files. Do not disable tests or checks to make a change pass.

If a change affects an Android-specific integration, run the relevant instrumentation test on an emulator or device and report what was tested.

## Issues and bug reports

Search existing issues before filing a new one. A useful report includes:

- expected and observed behavior;
- minimal reproduction steps;
- Android version and device/emulator type;
- app revision or version;
- relevant logs with credentials, API keys, personal data, and device identifiers removed.

For security vulnerabilities, do not open a public issue. Follow `SECURITY.md`.
