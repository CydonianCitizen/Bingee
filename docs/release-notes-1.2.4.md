# Bingee 1.2.4 — runtime reliability and container-aware statistics

These notes describe the implemented v1.2.4 changes. The [canonical development checklist](../BINGEE_DEVELOPMENT_ROADMAP_AND_CHECKLIST.md#v124-checklist) records verification separately. The hardware-efficiency audit (#7), final global audit and release gate remain open; this document does not announce a published release.

## Changed behavior

- Both home screen widgets observe Room, date and theme changes while their Glance sessions are active. Marking the next episode watched refreshes the displayed progress during that session. The application updater also wakes idle sessions, and poster loading follows URL changes. The episode action's public no-argument constructor is retained for Glance's reflective callback in optimized builds.
- Notification and widget detail intents for a different media type or TMDB ID create their own navigation entry and ViewModel. Back restores the previous title; repeated delivery for the same title remains single-top, including after activity recreation. Existing launcher shortcut and Statistics return paths remain intact.
- Home distinguishes a local read failure from an empty collection or calendar. Calendar, collection membership and Continue Watching retain their last successful values and offer recoverable feedback. Local Retry replaces all three observations; each warning clears only after its own successful read. A cached calendar stays visible, while a failure without cached events uses the existing error state. Calendar feedback takes priority over membership and continuation when multiple reads fail. Optional featured discovery and last-refresh metadata remain nonblocking.
- Collection, Details membership and personal-viewing statistics remain subscribed when the shared library-progress read fails. A safe failure is followed by automatic recovery and later Room updates on the original subscription. The shared source retries at one-second intervals with no attempt-count limit while observed. The last subscriber leaving starts the existing five-second grace period, after which the source and pending retry are cancelled. Observation starts with the first subscriber. Independent, unshared upstream DAO exceptions retain their existing terminal behavior and caller-owned resubscription.
- The Statistics taste radar uses its actual container width for its height cap and label geometry, including a narrow pane inside a wider window. Full-width phone rendering, relative Top 6 normalization, genre scopes, colors and the existing 260dp base height/font-scale cap of 1.5 remain unchanged. The empty radar follows the same sizing policy.

## Verification conventions

Instrumented UI tests resolve ordinary controls from localized resources and existing semantics; tests of visible wording and fixture titles retain explicit expectations. The locale rule applies English or Italian before Activity creation and restores the previous app locale without changing saved preferences. Notification Center component tests render the production content. See [Build and verify](../README.md#build-and-verify) for locale execution.

Release-only behavior is checked on the actual R8/minified artifact, including native notification taps, launcher shortcuts, both widget actions and offline relaunch. Debug instrumentation is complementary evidence. See [Optimized release smoke](../README.md#optimized-release-smoke) for the repeatable procedure; the canonical checklist retains the scoped results and remaining final gates.

The app remains local-first. App version is 1.2.4 with versionCode 8; Room schema 9, Backup format 3, portable user data and the privacy model are unchanged by these modifications.
