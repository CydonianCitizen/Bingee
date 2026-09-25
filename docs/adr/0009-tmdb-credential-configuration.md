# ADR 0009: Use one TMDB API Read Access Token with save-after-validation

- Status: Accepted
- Date: 2026-08-01

## Context

Bingee needs application-level access to TMDB metadata without introducing a Bingee account or backend. TMDB currently supports either a v3 API key query parameter or an API Read Access Token sent as a Bearer token. A runtime credential must remain local, must not enter Android or Bingee backups, and must not be exposed to UI state, routes, logs, or documentation.

Official sources checked on 2026-08-01:

- [TMDB application authentication](https://developer.themoviedb.org/docs/authentication-application)
- [TMDB validate-key endpoint](https://developer.themoviedb.org/reference/authentication-validate-key)
- [TMDB errors](https://developer.themoviedb.org/docs/errors)
- [TMDB attribution FAQ](https://developer.themoviedb.org/docs/faq)

## Decision

Support exactly one credential format for the MVP: the TMDB **API Read Access Token** available in the API section of a TMDB account. Send it only over HTTPS as `Authorization: Bearer …`. Validate it remotely with `GET https://api.themoviedb.org/3/authentication`.

Local validation trims surrounding whitespace and checks the RFC Bearer-token character structure. It does not assume a JWT shape or minimum length and never claims authorization. A structurally valid candidate is saved only after TMDB accepts it. Unauthorized responses reject the candidate; connectivity, rate-limit, server, malformed-response, and unknown failures do not prove rejection and do not erase an existing stored credential.

Encrypt the accepted token with AES-256-GCM. Generate and retain the non-exportable AES key in Android Keystore. Store only the versioned IV and ciphertext in `noBackupFilesDir`; Android excludes that directory from cloud backup and device transfer. Do not use deprecated AndroidX Security Crypto APIs, plain preferences, DataStore, Room, BuildConfig, navigation arguments, or saved state for the credential. Removing the credential deletes both ciphertext and its dedicated Keystore entry.

Persist only the non-sensitive first-run completion flag in a separate Preferences DataStore. A validated stored token remains trusted across startup until the user replaces, removes, or explicitly revalidates it. Startup performs local storage inspection only; it does not make a network call.

Users need a TMDB account to obtain the token, but Bingee itself has no account or proprietary backend. First-run configuration can be skipped. Home, Settings, and future local data remain accessible without a valid or currently verifiable token. Remote metadata features require usable TMDB configuration.

Future TMDB services may reuse the data-layer credential store to attach the same Bearer authorization without exposing the token to domain or UI code. Supporting another TMDB credential format later requires an explicit migration or superseding ADR; Bingee does not store parallel credentials today.

## Consequences

- The header avoids credentials in URLs, while the OkHttp graph contains no logging interceptor.
- Save-after-validation keeps rejected and temporarily unverifiable new candidates out of persistent storage.
- Replacing an existing credential is atomic at the encrypted-file boundary.
- Keystore loss or ciphertext corruption becomes an explicit unreadable-storage state that can be removed safely.
- No background validation or WorkManager task is introduced.
- Media search remains unimplemented until Milestone 3.
