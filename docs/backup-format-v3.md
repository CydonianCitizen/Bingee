# Bingee backup format v3

V3 is the current JSON export contract, independent of the Room schema version. Import accepts v1, v2, and v3. It retains the portable records and compatibility rules from [v1](backup-format-v1.md) and the genre identity, type-aware TMDB identity, movie runtime, and empty-season evidence from [v2](backup-format-v2.md).

## Portable preferences

V3 adds these required fields to `data.preferences`:

```json
{
  "theme": "SYSTEM_DEFAULT",
  "language": "ENGLISH",
  "hideEpisodeSpoilers": false,
  "profileDisplayModes": {
    "watchedMovies": "LIST",
    "watchedTvSeries": "LIST",
    "watchLaterMovies": "LIST",
    "watchLaterTvSeries": "LIST",
    "favoritesMovies": "LIST",
    "favoritesTvSeries": "LIST"
  }
}
```

`theme` is `SYSTEM_DEFAULT`, `LIGHT`, or `DARK`; `language` is `ENGLISH` or `ITALIAN`; each display mode is `LIST` or `GRID`. The existing portable notification lead time and release categories remain included. V1 and v2 remain valid and restore these new fields to their defaults: system theme, English, spoilers shown, and list view for all six collections.

Android notification permission and enablement, credentials and tokens, and device/runtime state remain excluded. Theme, language, spoiler visibility, display modes, and portable notification choices are stored in Room so restore replaces all portable data in one transaction. Room v8 migrates old appearance, spoiler, and display values from Preferences DataStore once; a Room marker prevents later reads from reapplying stale legacy values. This migration does not make notification permission or enablement portable. See [ADR 0031](adr/0031-portable-user-preferences-backup-v3-room-v8.md) and [privacy notes](privacy.md).

## Restore limits

An export is returned only when its UTF-8 JSON is no larger than 50 MiB, the same hard ceiling enforced by import. Exports exceeding it fail before any backup bytes are written or a share is launched; the Storage Access Framework may already have created the destination URI. V3 encoding and parsing enforce maxima of 50,000 media records, 100,000 seasons, 100,000 episode records, and 100,000 episode-progress records; export rejects counts the reader would reject. V1/v2 import retains its historical ceiling of 500,000 episodes and episode-progress records, still subject to the 50 MiB file limit. TV Time uses the same aggregate media, season, and episode ceilings as v3, so it cannot generate an import plan beyond those portable record limits.

The machine-readable contract is [bingee-backup-v3.schema.json](backup/bingee-backup-v3.schema.json). Pair consistency, references, and semantic limits are checked before database mutation.
