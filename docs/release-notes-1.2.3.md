# Bingee 1.2.3 — clarity and navigation fixes

These notes describe the implemented v1.2.3 changes. Verification and release readiness are recorded in the [canonical development checklist](../BINGEE_DEVELOPMENT_ROADMAP_AND_CHECKLIST.md#v123-checklist).

## Changed behavior

- Movie watched-date and Series completion-date actions wrap on narrow screens and at larger font sizes, keeping the clear-date action visible. Clearing a selected date preserves watch progress.
- Favorite toggles in Details and Collection list/grid share a neutral unchecked state and a red selected state, with artwork contrast preserved. Favorites shortcuts and filters keep their own roles.
- Search uses Add to collection / Remove from collection. Home Featured uses Add to collection / In collection. Italian uses Aggiungi alla raccolta / Rimuovi dalla raccolta / Nella raccolta. Membership alone no longer labels a title Watch Later; genuine Watch Later, Watching, Watched and Abandoned states retain their meaning.
- Details poster and backdrop artwork leave title identification to the visible heading, including missing/failed artwork. Back, Favorite and Refresh remain separately labelled and actionable.
- Notification thumbnails keep their compact dimensions and use the shared poster rounding. Release text, notification delivery and title navigation keep their existing behavior.
- Watching and Watch Later launcher shortcuts can open Statistics without a Your Bingee ancestor. Back/Up returns to Collection, then Home. Dashboard entry paths retain statistics selections and return through their existing stack.
- Your Bingee's populated Watching and Favorites shelves use saveable, provider-qualified keys that distinguish Movies from Series. Opening these shelves no longer crashes, and Favorites with the same provider ID remain distinct across state restoration.

The app remains local-first. These changes do not alter stored progress, ratings, membership, notification policy, Room schema 9 or Backup format 3. App version is 1.2.3 with versionCode 7.
