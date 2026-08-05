# Milestone 13 Finalization — Part 1 coverage matrix

Part 1 inventories Anime evidence, adds deterministic debug/test fixtures, and
adds state previews. Production behavior remains unchanged.

| Required behavior | Existing evidence | Missing evidence | Focused follow-up | Manual-only check | Production unchanged |
| --- | --- | --- | --- | --- | --- |
| Anime search result and empty result | JikanSearchMapperTest, JikanSearchClientTest | No Compose Anime result/empty evidence | Search screen state test | Preview/manual scan | Yes |
| Network-unavailable and rate-limit search errors | JikanSearchClientTest covers rate limit; generic error mapper covers UI text | No Anime-specific UI error evidence | Search ViewModel/Compose error tests | Preview/manual retry | Yes |
| Cached, stale, optional-field, unknown format/status details | JikanDetailsMapperTest covers normalization/enums | No repository freshness or Anime detail UI evidence | Details repository/ViewModel tests | Cached/stale previews | Yes |
| Known/unknown totals, ongoing/completed, movie progress | AnimeWatchProgressTest covers state derivation | No DefaultAnimeProgressRepository or detail interaction evidence | Progress repository and Compose tests | Progress previews | Yes |
| Local rating and provider score | BackupV2Test; generic rating tests | No Anime detail rating evidence | Anime detail rating test | Cached detail preview | Yes |
| Related entries stay separate/navigable | Mapper and backup validation cover references | No related-entry UI/navigation evidence | Details navigation test | Related-entry preview | Yes |
| Mixed TMDB/Jikan library and numeric-ID collision | BackupV2Test; generic identity tests | No mixed library UI/DAO evidence | Library/DAO focused tests | Mixed library preview | Yes |
| Anime premiere event | Calendar/backup tests cover persistence paths | No focused Home rendering evidence | Calendar/Home test | Premiere preview | Yes |
| Background refresh and provider isolation | DefaultCalendarRefreshCoordinatorTest; worker tests | No complete Anime worker/UI evidence | Worker/coordinator regression tests | Manual refresh | Yes |
| v1/v2 backup compatibility and validation | BackupV2Test, BackupDataStoreTest | Invalid Anime fixture breadth and restore UI evidence remain | Backup validation/restore tests | Restore flow | Yes |
| Anime route and current Compose instrumentation | DetailRouteTest, TMDB screen tests | No Anime detail instrumentation or related navigation test | Navigation/detail instrumentation | Emulator smoke test | Yes |

## Part 1 fixture catalog

FakeAnimeData contains synthetic search success/empty/failure results,
cached/stale details, optional-field and unknown-enum variants, known/unknown
episode totals, ongoing/completed/movie details, local rating, provider score,
relations, mixed library entries, same-number TMDB/Jikan identities, long
English/Japanese titles, and an Anime premiere event. No network, credentials,
history, or real user data are used.

## Part 1 preview matrix

Debug-only previews now cover Anime search result/empty/rate-limit states,
cached/stale details, known/unknown progress, movie progress, relations, dark
theme, large font, mixed TMDB/Jikan library, and an Anime premiere calendar
event.
