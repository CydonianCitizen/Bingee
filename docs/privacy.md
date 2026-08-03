# Privacy notes

Bingee is local-first and currently has no Bingee account, proprietary backend, analytics, or crash-reporting service.

## TMDB credential

Bingee supports a user-supplied TMDB API Read Access Token. After TMDB validates it, Bingee encrypts it locally with Android Keystore-backed key material and stores the ciphertext in Android's no-backup directory. The raw token is sent only to TMDB in the HTTPS Authorization header when an authenticated TMDB request is required.

The token is not stored in ordinary preferences, Room, navigation arguments, saved instance state, logs, screenshots, previews, or test fixtures. It is excluded from Android cloud backup, device transfer, and Bingee JSON data exports. Removing it deletes the encrypted file and the dedicated Keystore key. Temporary network or TMDB service failures do not erase an existing token.

## Current network behavior

Bingee calls TMDB's `GET /3/authentication` endpoint when the user explicitly validates or retries a credential. Search uses separate `GET /3/search/movie` and `GET /3/search/tv` requests after a 350 ms debounce. Details use `GET /3/movie/{movie_id}` or `GET /3/tv/{series_id}` only when cache policy or manual refresh requires it. Bingee does not validate on every startup and performs no background credential checks.

Search query text is held only in current screen memory. Bingee does not log it, persist search history, or cache result pages in long-lived storage. Normalized public title details and fetch timestamps are cached in Room for offline use; raw provider responses are not persisted. Cached details may belong to Library members or non-members and remain after Library removal. Posters/backdrops use constrained TMDB image URLs; Coil owns image memory/disk caching.

## TMDB attribution

Bingee uses TMDB and the TMDB APIs but is not endorsed, certified, or otherwise approved by TMDB. The TMDB logo shown in Settings/About is an approved TMDB attribution asset and remains less prominent than Bingee branding.
