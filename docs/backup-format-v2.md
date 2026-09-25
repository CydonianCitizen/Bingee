# Bingee backup format v2

V2 is a historical import contract, independent of the Room schema version. Current exports use [v3](backup-format-v3.md); import continues to accept v1, v2, and v3. All portable records, limits, privacy constraints, and replace-only transactional restore rules from [v1](backup-format-v1.md) remain applicable, with these additions:

Each `data.media` record requires an ordered `genres` array, with at most 100 items:

```json
"genres": [
  { "name": "Drama", "source": "TMDB", "genreId": 18 },
  { "name": "Legacy label", "source": null, "genreId": null }
]
```

Names must be nonblank and at most 8192 characters. Identity is either absent as a pair or a supported source (`TMDB`) plus a positive signed 64-bit integer ID. The ID identifies a genre, never a title. Array position preserves provider order; statistics count each title once per canonical identity regardless of duplicate localized labels.

Genres belong to the portable title, including watched titles removed from Library. They do not depend on `media_details`, remote refresh, or credentials. Restore remaps their parent IDs in the same transaction as progress and membership; it does not recreate detail freshness. Empty arrays mean no available genre metadata.

V1 imports without genres remain valid, but cannot recover metadata absent from the file. A later successful Details refresh can supply canonical genres. Readers limited to v1 reject v2 to prevent silent metadata loss.

## Title identity and movie runtime

TMDB numbers movies and TV series independently, so movie `1399` and series `1399` are unrelated titles. A title is identified by provider, `mediaType`, and external ID; two `data.media` records may share an ID when their types differ.

`data.library` and `data.ratings` entries carry an optional `mediaType` (`MOVIE` or `SERIES`) that selects between such a pair. Exports always write it. An entry without it, including every v1 entry, must match exactly one media record; an untyped reference to a shared ID is rejected as conflicting rather than guessed. Progress and abandoned-series records need no type: movie progress always refers to the movie and series records to the series.

Movie records carry an optional `runtimeMinutes` (a positive integer, or null when unknown). It keeps offline watch-time statistics intact after a restore, which does not recreate the Details cache. Series records leave it null; episode runtimes stay on episodes.

## Empty-season evidence

Each `data.seasons` record carries optional boolean `isKnownEmpty`, default false. Exports always write it. True means an episode response confirmed an empty season: `episodeCount` must be zero and no episode may reference that season. Contradictions reject the whole backup before any database mutation.

This is portable coverage evidence, not cache freshness. Restore preserves it while leaving fetch timestamps and cache language absent. Positive declared counts still prove coverage through an exact match with stored episode rows. Older v1/v2 files without the field remain valid, but cannot prove that a zero-count season was actually fetched; a successful refresh is needed to resolve that ambiguity. Older readers that ignore this optional field cannot preserve empty-season coverage on restore. See [ADR 0030](adr/0030-portable-empty-season-evidence-room-v7.md).

The machine-readable contract is [bingee-backup-v2.schema.json](backup/bingee-backup-v2.schema.json). Pair consistency and semantic limits are checked before database mutation.
