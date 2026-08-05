# Bingee 0.1.0-beta1 — draft release notes

Draft only. Do not publish from this repository.

Bingee is a local-first Android app for keeping a personal movie and TV-series history available without a proprietary account or cloud backend.

## Included

- TMDB movie and TV-series search and details;
- a local Library with add/remove, filters, and sorting;
- seasons and episodes, including watched progress;
- movie watched state and title-level ratings;
- a local release calendar;
- optional local release notifications;
- versioned JSON backup and replace-only restore.

## Important notes

- You provide and manage your own TMDB Read Access Token for remote metadata.
- Background refresh and notification delivery are approximate; Android can delay them.
- JSON backups are plaintext and are not encrypted or password protected. Keep them private.
- Bingee has no cloud synchronization or proprietary account.
- Anime/Jikan is not available yet.
- Experimental TV Time JSON ZIP import is outside this beta release scope; the later Milestone 12B implementation supports only one documented profile and is not a broad TV Time or generic third-party import claim.

Please report issues with the Android version, device, app version, steps to reproduce, and redacted logs. Do not send credentials, personal backups, URIs, or device identifiers.
