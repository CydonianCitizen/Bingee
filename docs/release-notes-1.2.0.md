# Bingee 1.2.0 Release Notes

Bingee `1.2.0` makes day-to-day tracking faster and brings Bingee to the home screen, with a redesigned Details screen and a refreshed look.

## Highlights

- **Home screen widgets**: two new widgets. The 2×2 widget shows the poster of the series you are watching, with a button that marks its next episode as watched. The 4×2 widget pairs that series with your next two releases. They read your local library only, work offline, and follow the theme you pick in Bingee.
- **One-tap episode tracking**: Continue Watching now sits below the featured rows. Each card shows the last episode you watched and the next one, and a ✓ button marks the next episode as watched without opening Details, with Undo.
- **Redesigned Details screen**: a backdrop hero with the poster, title, and genres; a top bar that fades in as you scroll; and a single scrolling list for seasons and episodes.
- **Refreshed look**: Oswald for headings and section titles, Inter for body text. Featured Films and Featured Series get separate rows, and the watchlist control stays readable on any poster: a dark disc with **+** to add, and a gold bookmark once a title is in your watchlist. The bookmark now marks the watchlist everywhere in the app.
- **Genres in backups**: exported backups now use format v2, which includes each title's genres. Backups in v1 and v2 can both be imported.

## Fixes

- Long seasons in Details are no longer cut off, and scrolling through episodes no longer fights the page scroll.
- Movie watched dates are preserved.
- Screens follow the in-app Light/Dark choice consistently, including drawable tints.
- The startup check and the first-run TMDB setup screen are readable in the dark theme.
- Accessibility improvements for season and episode controls.
- A movie and a TV series that share the same TMDB ID are now kept apart. Before, adding one could overwrite the other's watchlist entry, favorite, or rating.
- Restoring a backup keeps movie runtimes, so watch-time statistics are complete right away, even offline.
- Manual calendar refresh now checks every title in your library, not only the first 20.
- Home now shows a message when adding a title to the watchlist fails.
- Widget buttons have larger touch targets (48 dp).
- Home and the season list in Details load lazily, so long calendars and long-running series scroll smoothly.
- On tablets, in landscape, and in wide split-screen windows, the navigation moves to a side rail.
- The full SIL Open Font License texts for Oswald and Inter now ship with the app.

## Compatibility

- Updating from 1.1.0 keeps all your data. The local database moves to Room v5 with a one-time, non-destructive migration that tells movies and series with the same TMDB ID apart.
- Backups exported by 1.2.0 use format v2, which Bingee 1.1.0 and earlier cannot import. Keep this in mind before moving a backup to an older version.
- Minimum supported Android version: Android 13. Target Android version: Android 16.
- No account, cloud synchronization, or proprietary server is required.

## Known limitations

- Widgets use the system font, because Android widgets cannot load custom fonts.
- If marking an episode from a widget fails, the widget stays unchanged without an error message.
- A larger 4×4 widget is planned for a future release.

If you are on 1.1.0, you can find this update from **Your Bingee → Settings → About → Check for updates**.
