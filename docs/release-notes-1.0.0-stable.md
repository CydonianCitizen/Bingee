# Bingee 1.0.0-stable Release Notes

Bingee `1.0.0-stable` is the first stable, open-source, local-first Android release for tracking films and TV series.

## Highlights

- **Local-First & Data Ownership**: Full functionality works without an account, cloud sync, or proprietary backend. All library entries, progress, ratings, and calendar events are persisted locally in Room.
- **Movie & TV Series Search**: Remote metadata search powered by TMDB using user-configured API Read Access Token credentials.
- **Offline Library & Details**: Cache-first title details, TV seasons and episode metadata, episode/movie watch progress tracking, and personal 1–10 title ratings.
- **Home Release Calendar**: Room-first calendar displaying upcoming and recent movie releases, TV season premieres, and episode air dates.
- **Background Refresh & Notifications**: WorkManager-backed metadata refresh and configurable local notifications for upcoming releases with custom lead times.
- **TV Time Import**: Additive, offline history importer supporting TV Time JSON ZIP profiles.
- **Backup & Restore v3**: Versioned JSON backup/restore supporting full media history, link groups, and media equivalence audit records, alongside compatibility with backup v1 and v2 formats.
- **Anime Suspension & Data Preservation**: Anime catalogue UI and Jikan network calls are temporarily suspended in this release, while all pre-existing or imported Anime data remains securely stored and portable for future updates.

## Supported Scope

- Movies Search, Details, Progress, Ratings, and Library
- TV Series Search, Seasons, Episodes, Progress, Ratings, and Library
- Room Database v1 initial stable schema
- Backup export and restore versions 1, 2, and 3
- Storage Access Framework (SAF) system picker integration for privacy-safe file export and import

## Known Scope Exclusions

- Anime Search, Library filter, Details navigation, and Jikan remote refresh (temporarily suspended)
- Proprietary cloud sync or user accounts
- Social networking, comments, or personal recommendations
