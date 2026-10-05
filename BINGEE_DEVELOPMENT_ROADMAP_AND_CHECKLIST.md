# Bingee — Development Roadmap & Agent Execution Checklist

> **Purpose**
> This file is the canonical implementation roadmap for the next Bingee development cycle. It has two sections:
>
> 1. **PART I — Complete roadmap:** every planned change, the target app version, its scope, constraints, and acceptance criteria.
> 2. **PART II — Agent execution checklist:** the checklist the implementation agent must follow and tick for every change.
>
> This file is **canonical project documentation** and must be version-controlled. Generated audit reports are temporary artifacts and belong under `.audit/` only; they must not be committed by default.

---

# Baseline

Current baseline before this roadmap starts:

- App version: **v1.2.2**
- versionCode: **6**
- Room database: **9**
- Backup format: **3**
- Architecture: single-module Android app; Kotlin + Compose + Hilt + Room + Retrofit + WorkManager + Glance
- Product identity: **private personal Movie/TV Series tracker**, local-first where possible
- Not a social network, feed, public-profile product, or recommendation engine
- Current Details direction: **cinematic shared media hero + local-first personal tracker**
- Current repository health: **HEALTHY WITH TARGETED DEBT**

> Baseline correction (2026-10-04): pre-flight found Room 9 / Backup 3 already present at HEAD `837647f44a83ad9f9eab229877e71c925fb86de8`. The owner authorized continuing after the discrepancy was reported. Modification #1 preserves these existing persistence versions; no schema, migration or backup-format change is made.

## Global development rule from this point forward

**Every numbered modification in this roadmap must be followed by a GLOBAL read-only audit of the entire project before that modification can be marked complete.**

The audit exists to prevent progressive accumulation of:

- dead code;
- obsolete compatibility paths;
- duplicated business rules;
- speculative abstractions;
- unused dependencies;
- accidental architecture drift;
- UI/UX inconsistencies;
- accessibility regressions;
- responsive regressions;
- test bloat or stale tests;
- documentation drift.

The global audit must use, when available:

- **Impeccable** for UI/UX, accessibility, responsive behavior, hierarchy, Material 3 consistency and product coherence;
- **Ponytail Audit** for dead code, over-engineering, redundant abstractions, duplicate logic and unnecessary complexity;
- relevant **official Android Agent Skills** for Compose/adaptive UI, edge-to-edge, R8, testing, security and other Android-specific checks.

The audit is **read-only first**. It must distinguish:

- newly introduced debt caused by the current modification;
- pre-existing debt unrelated to the modification;
- justified complexity that should be left alone.

A modification is not complete until newly introduced P0/P1 issues and newly introduced proven dead code/over-engineering have been resolved or explicitly blocked with owner approval.

Pre-existing low-priority debt must not automatically expand the current scope.

---

# PART I — COMPLETE ROADMAP

# Version map

| Version | versionCode | Theme | Main objective |
|---|---:|---|---|
| **v1.2.3** | **7** | Small correctness / clarity fixes | Close low-risk UI/UX and semantics defects already proven by audits |
| **v1.2.4** | **8** | Technical / test / release polish | Remove small current technical risks without changing product architecture |
| **v1.2.5** | **9** | Dead-code cleanup + documentation sync | Dedicated repo-wide cleanup before the large UI cycle |
| **v1.3.0** | **10** | Major UI/UX #1 | Rebuild Details around a clear personal-tracker hierarchy |
| **v1.3.1** | **11** | Major UI/UX #2 | Reorganize Home around current personal activity |
| **v1.3.2** | **12** | Major UI/UX #3 | Make Collection a coherent personal-management surface |
| **v1.3.3** | **13** | Major UI/UX #4 | Unify Bingee's cross-screen UI language and interaction hierarchy |
| **v1.3.4** | **14** | Major UI/UX #5 | Comprehensive responsive + accessibility hardening |

> Version numbers may only move forward. Room and Backup versions must **not** be bumped merely because the app version changes. Schema/format bumps require an actual persistence contract change.

---

# v1.2.3 — Small correctness, clarity and semantics fixes

## Goal

Close the small, evidence-backed UI/UX defects that do not require a major screen redesign.

This release must remain intentionally narrow. It must not start any of the five major v1.3.x redesigns.

## 1. Fix watched/completion-date action layout under narrow width and large fonts

### Current problem

Runtime audit confirmed that the date action row can lose the visible **“Cancella data”** label at approximately 320dp / Italian / font scale 1.5, and becomes extremely narrow at approximately 360dp / Italian / 1.3.

### Required change

- Make the date action layout resilient to narrow width and large font scale.
- The clear/remove action must remain visibly identifiable.
- The Save/Edit/Cancel semantics must remain unambiguous.
- Verify both Movie watched-date and Series completion-date variants.
- Preserve existing persistence behavior.
- Do not introduce a new date component architecture unless necessary.

### Acceptance criteria

- 320dp + Italian + font 1.5: clear-date action remains visible and understandable.
- 360dp + Italian + font 1.3: no character-by-character collapse.
- 411dp normal layout remains visually balanced.
- English remains correct.
- Touch targets remain at least standard Material size.

---

## 2. Standardize Favorite toggle visual state where it is currently inconsistent

### Current problem

The Collection grid Favorite toggle can use Material primary/gold for the checked state while Details and Collection list use red/error semantics.

### Required change

For **actual Favorite toggles**:

- unselected heart: neutral/appropriate on-surface state;
- selected heart: consistent Favorite state treatment, expected to be red/error-family unless the owner explicitly changes the product convention;
- preserve artwork-specific contrast where needed;
- preserve shortcut/filter differences where they are not actual toggles.

Do not force the dashboard Favorites shortcut, dedicated Favorites filter, or static decorative heart to behave like a toggle.

### Acceptance criteria

- Details, Collection grid and Collection list communicate the same Favorite meaning.
- Checked semantics remain correct.
- Light/Dark contrast remains acceptable.

---

## 3. Stop equating generic Library membership with Watch Later in user-facing copy

### Current problem

Search and some Home/provider acquisition surfaces can present generic saved membership as **Watch Later**, even when a Series is Watching, Watched or Abandoned.

### Product rule

These concepts are different:

- **Library / Collection membership** = title belongs to the user's personal collection.
- **Watch Later** = canonical derived state for an unwatched/unstarted title.
- **Favorite** = independent personal marker.
- **Watching** = active Series progress.
- **Watched** = completed according to current canonical rules.
- **Abandoned** = explicit override.

### Required change

- Replace membership-only wording such as “In Watch Later” with membership-accurate wording such as **“In collection / In raccolta”** where the underlying state is only membership.
- Preserve genuine Watch Later wording only when the actual canonical state is Watch Later.
- Do not change persistence or state derivation.

### Acceptance criteria

- A Watching/Watched/Abandoned Series is never mislabeled as Watch Later solely because it is in Library.
- Removing from collection is clearly described as membership removal, not state-specific Watch Later removal.

---

## 4. Remove redundant Details artwork semantics

### Current problem

Backdrop, poster and visible title can each expose title-bearing accessibility semantics, potentially producing repeated title announcements.

### Required change

- Keep the visible title as the primary identity announcement.
- Mark purely decorative artwork as decorative where appropriate.
- Preserve meaningful image fallback semantics if an image itself conveys state that is not otherwise exposed.
- Do not merge unrelated interactive controls.

### Acceptance criteria

- Details no longer exposes three redundant title identity nodes.
- Back/Favorite/Refresh remain separately actionable and labeled.
- No title identity is lost.

---

## 5. Fix Notification poster clipping-layer inconsistency

### Current problem

Notification thumbnails can receive an outer 8dp clip while the shared poster component also applies its own 16dp rounding.

### Required change

- Keep the compact Notification poster size.
- Ensure there is one intentional effective clipping radius.
- Prefer reusing the shared poster behavior unless compact notifications genuinely require a distinct shape.
- Do not create a Notification-specific image component unless needed.

---

## 6. Validate the direct Collection shortcut → Statistics navigation risk

### Current concern

A launcher shortcut can enter Collection directly while Statistics may assume a Your Bingee ancestor exists in the back stack.

### Required operation

- Reproduce the route from a clean app/task state.
- Attempt Collection direct shortcut → Statistics.
- If it works correctly, document **NO DEFECT** and do not change code.
- If it fails, fix navigation ownership without creating synthetic back-stack machinery unless required.

### Acceptance criteria if a fix is required

- Statistics can be opened from all supported Collection entry paths.
- Back/Up behavior remains predictable.
- Normal Your Bingee → Collection → Statistics navigation is unchanged.

---

## 7. v1.2.3 documentation update

Update canonical docs only for user-visible semantics actually changed in this release.

Do not perform the broad documentation sync reserved for v1.2.5.

---

# v1.2.4 — Technical, test and release polish

## Goal

Resolve remaining small technical risks before beginning the large v1.3.x UI series.

No major UI redesign.

## 1. Reduce instrumentation tests' dependency on English display strings

### Current problem

Some Compose/instrumentation tests identify controls through literal English copy. This creates unnecessary fragility when copy changes and limits locale-independent testing.

### Required change

- Audit instrumented UI tests for literal-English selectors.
- Prefer stable semantics, roles, state descriptions, content descriptions, or resource-backed expectations where appropriate.
- Do not add test tags everywhere merely to make tests easy.
- Keep tests user-behavior oriented.
- Preserve tests where visible text itself is the behavior being tested.

### Acceptance criteria

- Key cross-screen tests do not fail simply because ordinary copy changes.
- At least representative Italian execution remains possible where relevant.

---

## 2. Release-runtime smoke coverage

### Required runtime smoke

Validate an R8/minified release artifact for:

- cold launch;
- TMDB credential validation;
- Search;
- Movie Details;
- Series Details;
- add/remove Library;
- Favorite;
- progress persistence;
- Your Bingee;
- Statistics;
- Settings;
- Notifications/deep link;
- launcher shortcuts;
- Glance/widget path if configured;
- relaunch persistence.

### Required checks

- no `FATAL EXCEPTION`;
- no `NoClassDefFoundError`;
- no `ClassNotFoundException`;
- no `VerifyError`;
- no release-only Gson/R8 regressions.

Do not add keep rules unless a demonstrated release failure requires them.

---

## 3. Review Home observation error handling

### Current concern

Some Home observation paths may silently swallow errors while other Home paths surface recoverable failures.

### Required operation

- Map current behavior.
- Decide whether the silent path is intentionally non-user-actionable.
- If intentional, leave it and document why.
- If accidental, align it with existing error/result conventions using the smallest change.

Do not create a global error bus.

---

## 4. Review shared Flow retry policy

### Current concern

A shared library-progress flow has a documented unconditional resubscribe delay after observation failure.

### Required operation

- Confirm the retry remains bounded and cancellation-safe.
- Confirm it does not spin while no subscriber exists.
- Confirm production consumers do not become permanently stale.
- Change only if there is a concrete behavior issue.

Do not replace a working simple retry with a complex backoff framework without evidence.

---

## 5. Statistics radar sizing hardening

### Current state

The radar uses actual canvas constraints for much of its geometry but still uses window width for part of its height cap.

### Required change

- Make radar sizing derive from its actual available container rather than the entire window where practical.
- Preserve current visual design and data semantics.
- Verify current phone layout remains unchanged or equivalent.
- Verify 600dp/adaptive layouts do not depend on full-window assumptions.

This is a layout correctness refinement, not a chart redesign.

---

## 6. v1.2.4 documentation update

Document only technical behavior materially changed by the above work.

---

# v1.2.5 — Dedicated global dead-code cleanup + canonical documentation sync

## Goal

Enter the major UI cycle with a deliberately lean, documented baseline.

This version is the dedicated cleanup release.

## 1. Run a fresh GLOBAL dead-code / over-engineering audit

Do not rely only on previous audit results. Re-scan the current v1.2.4 codebase.

Audit:

- unused production symbols;
- fake-only interface members;
- obsolete repository methods;
- dead DAO queries;
- wrappers with no current invariant;
- one-use abstractions that no longer justify themselves;
- stale compatibility paths;
- unused resources;
- stale strings/plurals;
- unreachable branches;
- obsolete test fixtures;
- duplicated rules that became redundant after v1.2.3/v1.2.4;
- unnecessary direct dependencies;
- stale documentation references.

Use call-site evidence before declaring anything dead.

## 2. Re-evaluate previously identified candidates

Re-check, do not blindly delete:

- `observeEntryCount` chain;
- repository-level `isInLibrary` if still unused;
- fake-only/default `observeContinueWatching` path;
- `SeasonSummaryStore` seam;
- unused portions of `LibraryOrganization`;
- stale helper aliases;
- dead UI wrappers;
- obsolete test-only seams.

If a candidate became useful, keep it.

## 3. Cleanup rules

- Delete only proven dead code.
- Prefer deletion over replacing dead code with a new abstraction.
- Do not split large files merely because they are large.
- Do not rewrite working architecture.
- Do not introduce future-history/year-recap support.

## 4. Full canonical documentation sync

Bring canonical docs to the actual v1.2.5 codebase:

- README;
- PRODUCT;
- AGENTS;
- architecture;
- roadmap;
- privacy;
- backup docs;
- schema docs;
- import docs;
- ADR index/references;
- release notes where maintained.

Historical ADR content must remain historical; do not rewrite old decisions to match current implementation.

## 5. Broken-link and stale-reference scan

- Scan all tracked Markdown.
- Validate relative links.
- Remove links to deleted generated audit reports.
- Confirm `.audit/` remains ignored.
- Confirm canonical docs remain tracked.

## 6. Establish v1.3.0 clean baseline

Before closing v1.2.5:

- no known P0/P1 blockers;
- no newly introduced dead code from cleanup itself;
- full verification green;
- global audit completed;
- documentation current.

---

# v1.3.0 — Major UI/UX #1: Details as a true personal-tracker screen

## Goal

Preserve the cinematic Details identity while making the user's personal relationship with a title immediately understandable.

## Product direction

**Hero → Personal summary → Synopsis → Detailed tracking/editing → Secondary metadata**

Do not revert the current hero.

## 1. Introduce a compact personal summary immediately below the hero

The summary must communicate state without exposing every editor by default.

### Movie summary information

At minimum evaluate and implement an appropriate presentation for:

- Library membership;
- Favorite state where useful without duplicating the toolbar toggle;
- watched/unwatched state;
- effective watched date when watched;
- current rating/unrated state;
- next meaningful action.

Do not duplicate the entire lower editing UI.

### Series summary information

At minimum:

- explicit personal relationship state;
- watched / currently trackable episode progress;
- season progress where useful;
- caught-up state where semantically applicable;
- next actionable episode when available;
- metadata-coverage limitations when they prevent a trustworthy completion judgment.

## 2. Make Series relationship state explicit

User-facing state vocabulary must clearly distinguish:

- Watch Later / Da vedere;
- Watching / In visione;
- Abandoned / Abbandonata;
- complete finished Series / Vista;
- caught up with an ongoing Series / In pari.

Important:

- Do not persist a new state merely for UI wording if it can be safely derived.
- Do not change canonical tracking semantics without an explicit separate product decision.
- “In pari” must not imply that an ongoing Series is permanently finished.

## 3. Add concise next-actionable-episode context

When a Series has an episode the user can meaningfully continue with, Details should identify it without forcing manual season searching.

Potential information:

- season/episode number;
- episode title;
- availability date if useful;
- direct scroll/open/action affordance if appropriate.

The source of truth must match Continue Watching policy. No second continuation algorithm.

## 4. Reduce rating default density

Move from an always-expanded editing experience toward:

- readable current rating/unrated state;
- editing on explicit user intent;
- preserve integer 1–10 semantics;
- preserve Save/Remove behavior and chronology;
- preserve accessibility.

Do not create a custom rating system incompatible with current data.

## 5. Make watched/completion date presentation semantically appropriate

### Movie

- Date editing should clearly belong to watched state.
- If date selection can mark an unwatched Movie as watched, the action must communicate that consequence.
- Existing effective date and custom date behavior must not become contradictory.

### Series

- Completion-date editing must only be presented when a genuine completion record/state makes it meaningful.
- Do not show a control that appears to save when persistence will ignore it.

## 6. Move Abandoned relationship control to title-level ownership

- Abandoned/Resume tracking must no longer require scrolling beyond arbitrarily long season content.
- Keep it visually secondary and reversible.
- Preserve episode history.
- Preserve current canonical override semantics.

## 7. Improve season/episode scan rhythm

Without redesigning the tracking model:

- reduce unnecessary visual noise;
- preserve collapsed-season scanning;
- preserve inline expansion;
- preserve Specials separation;
- preserve stale/loading/error states;
- improve long episode title allocation;
- keep future episodes visible but correctly disabled;
- make bulk season actions clearly scoped.

## 8. Clarify refresh scope

Title-level refresh and per-season episode refresh must not look like the same operation if they update different metadata scopes.

## 9. Preserve hero and media identity

Protect:

- backdrop;
- poster overlap;
- collapsing toolbar;
- contextual overlay controls;
- type/year/runtime-or-season-count orientation;
- cache-first content behavior.

## 10. Details verification matrix

At minimum:

- Movie / Series;
- new title / Library title;
- Watch Later / Watching / In pari / complete / Abandoned;
- rated / unrated;
- Favorite / not Favorite;
- Light / Dark;
- EN / IT;
- 320 / 360 / 411dp;
- font 1.0 / 1.3 / 1.5;
- long title;
- missing artwork;
- stale cached data;
- partial Series metadata;
- expanded season;
- future episode.

---

# v1.3.1 — Major UI/UX #2: Home centered on current personal activity

## Goal

Make Home answer: **“What is relevant for me right now?”** before provider browsing.

## 1. Re-evaluate section order

Target conceptual hierarchy:

1. Continue Watching / immediate personal continuation
2. Release calendar / current personal release activity
3. provider Featured Movies
4. provider Featured Series

Exact ordering may be adjusted only if runtime evidence gives a better personal-tracker hierarchy.

## 2. Protect Continue Watching semantics

- actionable Series only;
- no Movies;
- no Specials;
- no completed/caught-up Series without an actionable episode;
- no Abandoned Series;
- same canonical continuation policy used elsewhere.

## 3. Improve release-calendar role

- clearly distinguish release information from continuation actions;
- resolve Abandoned-series inclusion policy deliberately;
- align with Notification/release-center policy where appropriate without forcing identical scopes.

## 4. Keep Featured as secondary exploration

- preserve Featured if useful;
- make it visually and hierarchically secondary to personal activity;
- do not turn Featured into personalized recommendations;
- do not add ranking/recommendation algorithms.

## 5. Improve Home empty states

Empty personal sections should explain how the user can create useful Home content without pretending Featured is personal activity.

## 6. Preserve local-first behavior

- Room-backed content first;
- network refresh asynchronous;
- cached personal content retained during refresh failure.

## 7. Home verification matrix

Test:

- empty account;
- active Watching Series;
- upcoming release;
- stale cache;
- no credentials;
- Light/Dark;
- EN/IT;
- 320/360/411dp;
- font 1.0/1.3/1.5.

---

# v1.3.2 — Major UI/UX #3: Collection as a coherent management surface

## Goal

Make Collection the clearest place to browse, filter and manage personal titles without requiring the user to remember hidden route/state assumptions.

## 1. Make active Collection scope explicit

Clearly communicate when the user is viewing:

- Watch Later;
- Watching;
- Watched;
- Favorites;
- Abandoned.

Do not rely only on navigation history to explain the active scope.

## 2. Remove impossible filter combinations

Examples:

- Watching and Abandoned are Series-only; Movie filter choices must not create inherently empty nonsensical combinations.

Use the smallest context-aware control adaptation.

## 3. Clarify local search scope

The user must understand that Collection search filters the currently active personal scope, not global TMDB.

Evaluate whether current fragmented category search remains acceptable or whether a broader personal-title search is warranted. Do not add a global “All” mode unless the product owner explicitly approves it after evaluating complexity.

## 4. Align Grid and List management capabilities

Audit and intentionally decide where actions belong:

- Favorite;
- watched date;
- Library removal;
- Details navigation;
- rating display;
- progress display.

Grid and List do not need identical visual controls, but switching presentation must not unpredictably remove essential management capability.

## 5. Make sort semantics truthful

Resolve labels such as **Recently added** where the actual sort key differs by scope:

- library addition;
- Favorite addition;
- viewing activity.

Either make labels contextual or use terminology that matches the underlying chronology.

## 6. Improve filter discoverability

- retain horizontal scrolling only if later filters remain discoverable;
- avoid hidden state;
- preserve compact-width usability;
- do not create multi-row control overload without evidence.

## 7. Preserve Collection's role

Do not move deep episode tracking, detailed rating editing or abandonment management into Collection merely for convenience. Details remains the one-title relationship owner.

## 8. Collection verification matrix

Cover all scopes, grid/list, Movies/Series where valid, empty/non-empty, long titles, EN/IT, Light/Dark, 320/360/411dp, font 1.0/1.3/1.5.

---

# v1.3.3 — Major UI/UX #4: Cross-screen Bingee UI language unification

## Goal

Make Bingee feel like one product without flattening justified screen-specific behavior.

This release is about **shared visual/interaction rules**, not making all screens identical.

## 1. Define top-bar families

Create a small explicit set of justified chrome families, for example:

- top-level content screen;
- personal dashboard/collapsing screen;
- immersive artwork Details;
- secondary destination/settings screen.

Then align screens to those families.

Preserve justified exceptions.

## 2. Standardize Favorite meaning

- actual toggle state meaning consistent everywhere;
- selected semantics consistent;
- visual selected state consistent within artwork/non-artwork constraints;
- shortcut/filter/static heart contexts remain distinct where semantically different.

## 3. Standardize action hierarchy

Define and apply rules for:

- primary acquisition/action;
- secondary/reversible action;
- low-priority text action;
- destructive irreversible action;
- reversible relationship state;
- loading/pending action.

Do not color every reversible action red.

## 4. Standardize error / empty / loading expectations

Align:

- blocking error hierarchy;
- recoverable inline error;
- stale cached-content warning;
- empty-state title/body/action;
- loading announcement;
- retry/configuration action patterns.

Keep compact dashboard inline states where full-page states would be excessive.

## 5. Align section hierarchy

Review:

- section title roles;
- helper-copy roles;
- content spacing;
- dividers;
- cards versus bare shelves.

Do not force Statistics, Settings and media shelves into one identical layout.

## 6. Resolve release-center naming and scope clarity

Review whether the current user-facing **Notifications** destination name accurately describes a followed-Series release center containing past/current/future release events rather than a read/unread inbox.

If renamed:

- update navigation copy;
- empty state;
- settings references where needed;
- accessibility labels;
- docs.

Do not change system notification delivery semantics merely because the screen name changes.

## 7. Align Settings chrome

Resolve accidental drift between Settings index and subpages while preserving Settings' functional density.

## 8. Product-language consistency

Across the app, audit and align:

- Collection/Library;
- Watch Later;
- Watching;
- Watched;
- In pari / caught up;
- Abandoned;
- Favorite;
- release/update terminology;
- viewed/completed statistics wording.

Do not change domain persistence names merely to match UI copy.

---

# v1.3.4 — Major UI/UX #5: Comprehensive responsive + accessibility hardening

## Goal

After the previous UI changes settle, validate the whole application under real constraints and fix remaining responsive/accessibility debt globally.

This is a dedicated hardening release, not a redesign release.

## 1. Full responsive matrix

At minimum:

### Widths

- ~320dp
- ~360dp
- ~411dp
- >=600dp

### Font scale

- 1.0
- 1.3
- 1.5

### Locale

- English
- Italian

### Theme

- Light
- Dark

Prioritize adversarial combinations:

- 320dp + Italian + 1.5;
- 360dp + Italian + 1.3;
- 411dp normal;
- >=600dp with adaptive navigation/layout.

## 2. Screen coverage

Cover:

- Home;
- Search;
- Your Bingee;
- Collection;
- Movie Details;
- Series Details;
- Statistics;
- release/Notifications center;
- Settings;
- key dialogs;
- key loading/empty/error states.

## 3. Fix fixed-width text starvation

Review:

- hero poster + text;
- episode still + text/actions;
- Collection list poster + metadata/actions;
- dashboard shortcuts;
- date/action rows;
- filter rows;
- chart labels.

Avoid brittle per-device magic numbers.

## 4. Wide-screen behavior

- prevent absurd full-width text lines;
- ensure rail layouts use actual remaining content width;
- review chart/container sizing;
- introduce max-width/content constraints where warranted;
- do not build a tablet two-pane architecture unless the product benefits justify it.

## 5. Accessibility semantics

Audit globally:

- headings;
- roles;
- selected/checked state;
- disabled state;
- touch targets;
- decorative imagery;
- duplicate identity announcements;
- progress descriptions;
- chart descriptions;
- filter states;
- season/episode controls;
- Favorite;
- navigation icons;
- dialogs;
- live regions;
- error recovery.

## 6. TalkBack/manual pass

Where practical, perform a bounded manual TalkBack pass of the highest-value flows:

- Home → Details;
- Search → Details;
- Series episode tracking;
- Collection filtering;
- Statistics interaction;
- Settings/backup destructive confirmation.

Do not claim spoken-order correctness without actually testing it.

## 7. Contrast

Validate:

- Light/Dark system bars;
- text over artwork;
- selected/unselected controls;
- disabled controls;
- chart marks;
- progress indicators;
- error/destructive controls.

## 8. Final UI system audit

Run a final Impeccable whole-app audit and confirm:

- remaining differences are intentional;
- no major screen still uses an obsolete visual dialect;
- no P0/P1 accessibility or responsive issue remains;
- large UI work did not accumulate redundant components or dead styling paths.

---

# PART II — AGENT EXECUTION CHECKLIST

# A. Mandatory checklist for EVERY numbered modification

> **Rule:** A numbered modification from Part I cannot be marked complete until every applicable item below is checked.

## A1. Pre-flight

- [ ] Read this roadmap section completely before changing code.
- [ ] Inspect `git status --short`.
- [ ] Record branch and HEAD.
- [ ] Confirm current `versionName` and `versionCode`.
- [ ] Confirm current Room version.
- [ ] Confirm current Backup version.
- [ ] Identify unrelated working-tree changes and preserve them.
- [ ] Load Impeccable when the modification touches UI/UX/accessibility/responsive behavior.
- [ ] Load Ponytail Audit for the post-change global audit.
- [ ] Load relevant official Android skills when the modification touches Android-specific concerns.
- [ ] Map the current implementation and all real call sites before editing.
- [ ] Write down the existing invariant/behavior being preserved.

## A2. Scope protection

- [ ] Confirm the change belongs to the CURRENT roadmap item/version.
- [ ] Do not implement tasks from later versions.
- [ ] Do not add future History / Year Recap architecture.
- [ ] Do not bump Room unless the persistence schema truly changes.
- [ ] Do not bump Backup unless the portable format truly changes.
- [ ] Do not introduce a framework/service/interface merely to remove a few repeated lines.
- [ ] Do not split files merely because they are large.
- [ ] Do not refactor unrelated code while “already in the area.”

## A3. Implementation

- [ ] Make the smallest change that satisfies the product requirement.
- [ ] Reuse existing canonical policies/helpers before adding new ones.
- [ ] Keep Movie/Series asymmetry where semantics require it.
- [ ] Keep Library membership independent from Favorite/rating/history semantics.
- [ ] Preserve Specials exclusion from canonical regular-Series completion.
- [ ] Preserve current local-date source for date-bound availability logic.
- [ ] Preserve TMDB-first navigable identity rules.
- [ ] Preserve cache-first/local-first behavior.
- [ ] Preserve user data during visual/UX changes.
- [ ] Add/modify strings in EN and IT together.
- [ ] Use plurals/resources rather than concatenated user-facing text where applicable.

## A4. Focused verification

- [ ] Add or update focused tests only where behavior ownership changed.
- [ ] Run the narrowest relevant JVM tests.
- [ ] Run the narrowest relevant connected tests when Android/runtime behavior changed.
- [ ] If UI changed, inspect at least one Light and one Dark runtime state.
- [ ] If layout changed, inspect relevant narrow-width/large-font combination.
- [ ] If locale-sensitive, inspect Italian.
- [ ] Do not use arbitrary sleeps to stabilize reactive tests.
- [ ] Classify failures before changing production code.

## A5. Mandatory GLOBAL project audit after the modification

Run a read-only whole-project audit covering all of the following, even if the modification touched one small area:

### Architecture

- [ ] Feature → domain → data boundaries still hold.
- [ ] No new UI → DAO/Retrofit leakage.
- [ ] No unjustified repository/ViewModel responsibility growth.
- [ ] No new speculative layer/interface/factory.

### Ponytail / simplicity

- [ ] Search for newly dead production symbols.
- [ ] Search for wrappers added but no longer needed.
- [ ] Search for single-use abstractions created by the change.
- [ ] Search for duplicated business rules created by the change.
- [ ] Search for obsolete branches/resources left behind.
- [ ] Estimate complexity delta.
- [ ] Explicitly identify justified complexity that should remain.

### Persistence

- [ ] Room migrations/schemas still coherent.
- [ ] Backup format/restore behavior still coherent.
- [ ] No accidental persistence semantics change.
- [ ] No personal-state loss path introduced.

### Flow / concurrency

- [ ] No duplicate broad Room collectors introduced.
- [ ] No stale replay/`first()` test anti-pattern introduced.
- [ ] No blocking work on Main introduced.
- [ ] Cancellation remains correct.
- [ ] Retry behavior remains bounded.

### Performance

- [ ] No obviously eager/unbounded list rendering introduced without need.
- [ ] No repeated expensive mapping/query introduced in recomposition.
- [ ] No unnecessary caching added.
- [ ] Any performance concern is labeled measured/source-supported/speculative.

### Security / privacy

- [ ] No token/credential logging.
- [ ] No secret moved into BuildConfig/export/backup.
- [ ] No exported component/intents broadened unintentionally.
- [ ] Private-tracker data model remains private by default.

### UI / UX — Impeccable

- [ ] Hierarchy remains coherent.
- [ ] New UI uses existing Bingee typography/color/spacing unless intentionally changed.
- [ ] New controls have appropriate action weight.
- [ ] Loading/empty/error states remain coherent.
- [ ] No accidental competing visual dialect introduced.

### Accessibility

- [ ] Interactive icons have labels.
- [ ] Toggle state is exposed.
- [ ] Decorative images are not unnecessarily announced.
- [ ] Touch targets remain adequate.
- [ ] Headings/roles/state descriptions remain correct.
- [ ] No duplicate semantic node introduced.

### Responsive

- [ ] No new clipping at 320/360/411dp.
- [ ] No new large-font failure.
- [ ] Italian strings do not break the changed surface.
- [ ] 600dp behavior does not assume full window width where inappropriate.

### Tests

- [ ] New tests verify behavior rather than implementation details.
- [ ] No redundant low-value test duplication.
- [ ] No English-literal selector added unnecessarily.
- [ ] No flaky timing workaround added.

### Documentation

- [ ] Canonical docs still match changed behavior.
- [ ] No generated audit report added to `docs/`.
- [ ] `.audit/` remains ignored.
- [ ] Historical ADRs were not rewritten as current documentation.

## A6. Audit remediation gate

- [ ] Fix all P0 issues introduced by the modification.
- [ ] Fix all P1 issues introduced by the modification.
- [ ] Remove proven dead code introduced by the modification.
- [ ] Remove unjustified abstractions introduced by the modification.
- [ ] Do not automatically absorb unrelated pre-existing P2/P3 debt into scope.
- [ ] Record pre-existing findings separately if discovered.
- [ ] Re-run focused verification after remediation.

## A7. Modification completion record

- [ ] Record files changed.
- [ ] Record production LOC added/removed approximately.
- [ ] Record abstractions added/removed.
- [ ] Record any intentional duplication left and why.
- [ ] Record any deferred finding and target version.
- [ ] Confirm Room version.
- [ ] Confirm Backup version.
- [ ] Confirm app version remains the correct target for the current milestone.

---

# B. Release gate checklist for EVERY version

## B1. Version

- [ ] Set exact `versionName` from the Version Map.
- [ ] Set exact `versionCode` from the Version Map.
- [ ] Confirm no accidental Room/Backup bump.

## B2. Full verification

Run and report exact PASS / FAIL / NOT RUN:

- [ ] `git diff --check`
- [ ] focused JVM tests
- [ ] full `testDebugUnitTest`
- [ ] focused connected tests where relevant
- [ ] full `connectedDebugAndroidTest`
- [ ] `spotlessCheck`
- [ ] `lintDebug`
- [ ] `assembleDebug`
- [ ] `assembleRelease`
- [ ] `assembleDebugAndroidTest`

For release-oriented milestones also:

- [ ] build/sign the intended release artifact when requested;
- [ ] verify signer;
- [ ] smoke exact signed/minified artifact;
- [ ] compute SHA-256 only after the final artifact is frozen.

## B3. UI regression matrix for UI-touching releases

At minimum:

- [ ] 320dp / Italian / font 1.5
- [ ] 360dp / Italian / font 1.3
- [ ] 411dp / normal font
- [ ] 600dp where relevant
- [ ] Light
- [ ] Dark
- [ ] representative Movie
- [ ] representative Series
- [ ] empty state
- [ ] populated state
- [ ] loading/error state where changed

## B4. Final global audit

Even though every modification already receives a global audit, run one final whole-project synthesis before closing the version.

- [ ] architecture
- [ ] Ponytail / over-engineering
- [ ] dead code
- [ ] duplication/business rules
- [ ] Room/Backup
- [ ] Flow/concurrency
- [ ] performance
- [ ] R8/release robustness
- [ ] tests
- [ ] security/privacy
- [ ] Impeccable UI/UX
- [ ] accessibility
- [ ] responsive/adaptive
- [ ] localization
- [ ] documentation

The final audit must explicitly classify:

- [ ] FIXED IN THIS VERSION
- [ ] PRE-EXISTING / DEFERRED
- [ ] JUSTIFIED / LEAVE ALONE

## B5. Documentation

- [ ] Update canonical docs required by the release.
- [ ] Update roadmap status.
- [ ] Update release notes if maintained.
- [ ] Validate Markdown links.
- [ ] Confirm no `.audit/` artifact staged.

## B6. Git hygiene

- [ ] `git status --short` reviewed.
- [ ] Only intended files staged.
- [ ] No build artifacts staged.
- [ ] No secrets staged.
- [ ] No generated audit reports staged.
- [ ] No unrelated user work overwritten.

## B7. Release decision

Choose exactly one:

- [ ] READY TO COMMIT
- [ ] NOT READY

If READY TO COMMIT, report:

- [ ] recommended commit scope/message;
- [ ] exact version/versionCode;
- [ ] Room/Backup versions;
- [ ] test totals;
- [ ] remaining deferred findings.

---

# C. Version-specific agent checklists

# v1.2.3 checklist

- [x] Bump to v1.2.3 / code 7.
- [x] Fix date-action responsive failure.
- [x] Verify Movie and Series date variants.
- [x] Standardize real Favorite toggles.
- [x] Replace Library-as-Watch-Later wording where semantically wrong.
- [x] Remove redundant Details artwork semantics.
- [x] Resolve Notification poster clipping-layer inconsistency.
- [ ] Validate direct Collection shortcut → Statistics navigation.
- [ ] Fix navigation only if reproduced.
- [ ] Update directly affected canonical docs.
- [ ] Run mandatory GLOBAL audit after each numbered modification.
- [ ] Run final v1.2.3 global audit.
- [ ] Pass release gate.

Modification #1 completion record (2026-10-04): Part II A1–A7 applied; shared date actions now use native Compose `FlowRow`. Movie watched dates and genuinely completed Series dates passed 320dp / IT / 1.5 / Dark, 360dp / IT / 1.3 / Light and 411dp / EN / 1.0 / Light runtime checks, including labels, button roles, touch targets, Edit/Cancel/Save/Clear and retained progress after clearing. Focused JVM tests (25) and connected Details/Series DAO tests (26) passed; changed-file Spotless, lintDebug and debug/test builds passed. Full Spotless still reports pre-existing violations in unrelated files.

- [x] Mandatory GLOBAL read-only audit for **modification #1 only**, covering all eleven A5 categories with Impeccable, Ponytail Audit and official Android adaptive/security skills. No introduced P0/P1, dead code or unjustified abstraction found; pre-existing findings deferred to their planned items. Evidence and the scoped A checklist are temporary under `.audit/v1.2.3-mod1/`. This does **not** complete the all-modifications audit item, final v1.2.3 global audit or release gate above.

Modification #2 completion record (2026-10-04): Part II A1–A7 applied; Collection grid now explicitly uses neutral `contentColor`, semantic `error` for `checkedContentColor`, and the same artwork-safe surface container in both states. Details white-overlay interpolation and opaque error state, Collection list tint, textual overflow action, navigation shortcuts/shelf and Favorites filter remain intact. Native checked roles, resource-backed action labels, round-trip toggling and >=48dp touch targets passed. Final runtime evidence covers populated title Details (overlay and fully opaque toolbar), list and grid in both states at 320dp / IT / 1.5 and 411dp / EN / 1.0, each in Light and Dark. Focused JVM tests (34), connected Favorite tests (3), four runtime matrix cases, changed-file Spotless, lintDebug and debug/test builds passed. Full Spotless fails only on pre-existing formatting in unrelated files. Production Kotlin +3/-5 LOC; no production abstraction or dependency added. App 1.2.3 / code 7, Room 9 and Backup 3 unchanged; no migration or backup-format change. Only `ProfileScreen.kt`, focused `ProfileScreenTest.kt` and this canonical roadmap changed; no commit or staging performed.

- [x] Mandatory GLOBAL read-only audit for **modification #2 only**, covering all eleven A5 categories with Impeccable 4.2.2, Ponytail Audit and official Android adaptive/security skills. No introduced P0/P1, regression, dead code or unjustified abstraction found. Existing retry/Home/radar concerns remain v1.2.4; source-proven unused API/seam candidates and documentation drift remain v1.2.5; other v1.2.3 and v1.3.x UI work remains deferred. Evidence, findings classification and the scoped A checklist are temporary under `.audit/v1.2.3-mod2/`. This does **not** complete the all-modifications audit item, final v1.2.3 global audit or release gate above.

Modification #3 completion record (2026-10-04): Part II A1–A7 applied. Search now labels membership actions Add to collection / Remove from collection; Home Featured uses Add to collection / In collection with a Material plus/check instead of the saved bookmark. EN/IT resources share the same membership meaning. Existing add/remove behavior, Home's add-only saved state, Details Library terminology, genuine Watch Later categories/counts/shortcuts and Favorite independence remain intact. No ViewModel/domain/persistence change or new reactive query. Focused JVM tests (24), connected Search/Home tests (18), full Spotless, lintDebug (0 errors; 51 pre-existing warnings), assembleDebug and assembleDebugAndroidTest passed. Native runtime checks cover populated Movie and Series membership states on Search/Home at 320dp / IT / font 1.3 / Dark and 411dp / EN / font 1.0 / Light, including action callbacks, native button roles and >=48dp touch targets. Production Kotlin +20/-22 and resources +6/-8 LOC; no abstraction or dependency added. App 1.2.3 / code 7, Room 9 and Backup 3 unchanged. Files: SearchScreen.kt, HomeScreen.kt, one corrected YourBingeeScreen.kt comment, EN/IT strings.xml, SearchScreenTest.kt, HomeScreenTest.kt and this roadmap. Existing modification #2 work preserved; no commit or staging performed for #3.

- [x] Mandatory GLOBAL read-only audit for **modification #3 only**, covering all eleven A5 categories with Impeccable 4.2.2, Ponytail Audit and official Android adaptive/security guidance. No introduced P0/P1, regression, dead code, unjustified abstraction or additional query cost found. Details artwork/Notification clipping remain their later v1.2.3 items; existing Home error handling, retry/radar/test-selector concerns remain v1.2.4; unused API/seam candidates and broad documentation drift remain v1.2.5. Inventory, scoped checklist, audit and runtime evidence are temporary under `.audit/v1.2.3-mod3/`. This does **not** complete the all-modifications audit item, final v1.2.3 global audit or release gate above.

Modification #4 completion record (2026-10-05): Part II A1–A7 applied after reading this entire canonical roadmap before implementation inspection. Clean pre-flight on `main` at `c4baae4aee0f3303377654d56cb5a66ee31998f1`, with full Spotless PASS. Runtime/source mapping confirmed separate title-bearing backdrop and poster descriptions plus the visible title heading, with no hero-wide merge. Details now passes `contentDescription = null` through the existing MediaPoster API and both backdrop image branches; loaded, missing and failed artwork remains decorative. Original title, metadata, complete heading text despite visual ellipsis, toolbar labels/actions/Favorite state and all rendering/loading/cache configuration remain unchanged. Removed only the now-unused backdrop description resources in EN/IT. Two focused semantics tests cover Movie/Series, missing/non-null local artwork, authoritative title heading, retained original title/metadata and accessible toolbar. Focused JVM tests (18), connected Details/MediaPoster tests (17), full Spotless, lintDebug (0 errors; 51 pre-existing warnings), assembleDebug, assembleDebugAndroidTest and diff check passed. Pixel_9 runtime checks cover Movie/Series at 411dp / EN / font 1.0 in Light/Dark with loaded, missing and failed local artwork, including separate >=48dp toolbar targets and callbacks. No visible UI change: 11/12 captures are byte-identical; one synthetic poster capture differs only along a 441-pixel internal raster column, with unchanged geometry/title/toolbar. Runtime semantics PASS; TalkBack NOT RUN. Production Kotlin +4/-3 and resources +0/-4 LOC; no abstraction or dependency added. App 1.2.3 / code 7, Room 9 and Backup 3 unchanged. Files: DetailHero.kt, MediaDetailsScreenTest.kt, EN/IT strings.xml and this roadmap. Modifications #1–#3 preserved; #5–#7, final v1.2.3 audit and release gate remain open.

- [x] Mandatory GLOBAL read-only audit for **modification #4 only**, covering all eleven A5 categories with Impeccable 4.2.2, Ponytail Audit and official Android adaptive/security/Compose accessibility guidance. No introduced P0/P1, accessibility regression, dead code, unjustified abstraction, persistence/concurrency change or additional image work found. Notification clipping and shortcut navigation remain #5/#6; existing Home failure handling, retry, radar and test-selector concerns remain v1.2.4; unused API/seam candidates and broad documentation drift remain v1.2.5. Shared poster defaults and meaningful imagery outside Details are protected. Inventory, scoped checklist, audit, verification logs, runtime trees and visual evidence are temporary under `.audit/v1.2.3-mod4/`. This does **not** complete the all-modifications audit item, final v1.2.3 global audit or release gate above.

Modification #5 completion record (2026-10-05): Part II A1–A7 applied from clean `main` at `76e7919f2c49301bc8c81043590baf59bcadce71`. Notifications now delegates thumbnail clipping entirely to the existing `MediaPoster` Material shape (16dp), removing the redundant outer 8dp clip and unused import. The 48×72dp size, card shape, loading/fallback behavior, decorative image semantics, text and navigation remain unchanged. Notifications ViewModel JVM tests (12), existing Notifications/MediaPoster connected tests (5), full Spotless, lintDebug (0 errors; 51 pre-existing warnings), assembleDebug and assembleDebugAndroidTest passed. An ignored harness exercises the actual production screen and lifecycle-owned ViewModel with synthetic local fixtures: 411dp / EN / font 1.0 in Light/Dark before and after, plus 320dp / IT / font 1.0 / Dark and a resized 600dp / EN / font 1.0 / Light window after. Loaded/missing/failed artwork, compact dimensions, decorative semantics, card touch bounds and Details/Back callbacks passed. Both 411dp before/after comparisons have zero different pixels. Final standard APK builds exclude the temporary harness. No permanent implementation-shaped test was added for this modifier deletion. Large-font runtime and TalkBack NOT RUN. Production Kotlin +1/-4 LOC; no abstraction, dependency or reactive query added. App 1.2.3 / code 7, Room 9 and Backup 3 unchanged. Files: NotificationsScreen.kt and this roadmap. Modifications #1–#4 preserved; #6–#7, final v1.2.3 audit and release gate remain open.

- [x] Mandatory GLOBAL read-only audit for **modification #5 only**, covering all eleven A5 categories with Ponytail Audit, Impeccable 4.2.2 and official Android adaptive/intent-security guidance. No introduced P0/P1, regression, dead code, unjustified abstraction or persistence/concurrency change found. Existing Home failure handling, retry, radar and test-selector concerns remain v1.2.4; NotificationsScreenTest's test-local replacement also merits production-coverage review during v1.2.4 test/release work. Unused API/seam candidates and broad documentation drift remain v1.2.5. Inventory, scoped checklist, audit, verification logs and runtime evidence are temporary under `.audit/v1.2.3-mod5/`. This does **not** complete the all-modifications audit item, final v1.2.3 global audit or release gate above.

# v1.2.4 checklist

- [ ] Bump to v1.2.4 / code 8.
- [ ] Audit English-literal instrumentation selectors.
- [ ] Replace only brittle selectors with semantic/resource-based equivalents.
- [ ] Run release/minified smoke including Glance, notification deep links and shortcuts.
- [ ] Review Home observation error swallowing.
- [ ] Change only if inconsistent behavior is proven.
- [ ] Review shared Flow retry policy.
- [ ] Change only if a concrete issue is proven.
- [ ] Make radar sizing container-aware where window assumption remains.
- [ ] Update directly affected docs.
- [ ] Run mandatory GLOBAL audit after each numbered modification.
- [ ] Run final v1.2.4 global audit.
- [ ] Pass release gate.

# v1.2.5 checklist

- [ ] Bump to v1.2.5 / code 9.
- [ ] Run fresh repo-wide Ponytail dead-code audit.
- [ ] Re-evaluate every known historical dead-code candidate.
- [ ] Remove only proven dead production code.
- [ ] Remove obsolete resources/tests/fixtures only with evidence.
- [ ] Do not perform architecture rewrites.
- [ ] Perform full canonical documentation sync.
- [ ] Scan all tracked Markdown links.
- [ ] Confirm `.audit/` ignored and canonical `docs/` tracked.
- [ ] Run mandatory GLOBAL audit after each cleanup batch.
- [ ] Run final v1.2.5 global audit.
- [ ] Establish clean v1.3.0 baseline.
- [ ] Pass release gate.

# v1.3.0 checklist — Details

- [ ] Bump to v1.3.0 / code 10.
- [ ] Preserve hero/backdrop/poster/collapsing chrome direction.
- [ ] Design and implement compact personal summary.
- [ ] Movie summary communicates watched/date/rating/membership appropriately.
- [ ] Series summary exposes explicit personal relationship state.
- [ ] Derive “In pari” without unnecessary persistence state.
- [ ] Reuse canonical continuation policy for next episode.
- [ ] Add next-actionable-episode context.
- [ ] Compact rating editing behind explicit intent.
- [ ] Preserve 1–10 rating persistence/chronology.
- [ ] Make Movie date behavior explicit and truthful.
- [ ] Show Series completion date only when meaningful.
- [ ] Move Abandoned/Resume to title-level personal relationship area.
- [ ] Improve season scan hierarchy.
- [ ] Improve episode long-text allocation.
- [ ] Preserve Specials behavior.
- [ ] Preserve stale/cache/error states.
- [ ] Clarify title refresh vs season refresh scope.
- [ ] Run full Details state matrix.
- [ ] Run mandatory GLOBAL audit after each numbered modification.
- [ ] Remove any old Details UI paths made dead by redesign before release.
- [ ] Run final v1.3.0 global audit.
- [ ] Update canonical docs for new Details IA.
- [ ] Pass release gate.

# v1.3.1 checklist — Home

- [ ] Bump to v1.3.1 / code 11.
- [ ] Make personal continuation the primary Home task.
- [ ] Reorder or restructure Home sections accordingly.
- [ ] Preserve actionable Continue Watching policy.
- [ ] Preserve calendar value and clarify its role.
- [ ] Deliberately resolve Abandoned-series calendar inclusion policy.
- [ ] Keep Featured secondary and non-personalized.
- [ ] Improve personal empty states.
- [ ] Preserve local-first/cached behavior.
- [ ] Verify Home with empty, populated, stale and no-credential states.
- [ ] Run mandatory GLOBAL audit after each numbered modification.
- [ ] Remove obsolete Home layout/control paths made dead by redesign.
- [ ] Run final v1.3.1 global audit.
- [ ] Update canonical docs.
- [ ] Pass release gate.

# v1.3.2 checklist — Collection

- [ ] Bump to v1.3.2 / code 12.
- [ ] Make active Collection scope explicit.
- [ ] Remove impossible media-type/scope combinations.
- [ ] Clarify Collection-local search scope.
- [ ] Decide whether broader “All personal titles” scope is needed; do not implement without owner approval.
- [ ] Align essential Grid/List management capabilities.
- [ ] Make sort labels match actual chronology per scope.
- [ ] Improve filter discoverability.
- [ ] Keep deep tracking/rating ownership in Details.
- [ ] Verify every Collection scope in grid/list and empty/populated states.
- [ ] Run mandatory GLOBAL audit after each numbered modification.
- [ ] Remove obsolete Collection paths/components made dead by redesign.
- [ ] Run final v1.3.2 global audit.
- [ ] Update canonical docs.
- [ ] Pass release gate.

# v1.3.3 checklist — Cross-screen design language

- [ ] Bump to v1.3.3 / code 13.
- [ ] Define explicit top-bar/chrome families.
- [ ] Align screens to those families.
- [ ] Preserve justified Details and dashboard exceptions.
- [ ] Standardize Favorite toggle meaning.
- [ ] Standardize primary/secondary/reversible/destructive action hierarchy.
- [ ] Standardize blocking/recoverable/stale/empty/loading expectations.
- [ ] Align section heading/helper-copy conventions.
- [ ] Review release-center name and scope.
- [ ] Rename only with owner approval.
- [ ] Align Settings index/subpage chrome.
- [ ] Run full terminology audit across Collection/Library/Watch Later/Watching/Watched/In pari/Abandoned/Favorite/releases.
- [ ] Keep domain persistence names unchanged unless actual domain semantics change.
- [ ] Run mandatory GLOBAL audit after each numbered modification.
- [ ] Remove obsolete shared/local styling paths made dead by alignment.
- [ ] Run final v1.3.3 global audit.
- [ ] Update canonical design/product docs.
- [ ] Pass release gate.

# v1.3.4 checklist — Responsive & accessibility hardening

- [ ] Bump to v1.3.4 / code 14.
- [ ] Execute full width/font/locale/theme matrix.
- [ ] Cover all major screens and key dialogs/states.
- [ ] Fix any remaining fixed-width text starvation.
- [ ] Fix any remaining non-wrapping action failure.
- [ ] Validate hero and episode layouts at 320/360dp.
- [ ] Validate 600dp remaining-content width behavior.
- [ ] Add max-width constraints only where evidence justifies them.
- [ ] Audit global semantics.
- [ ] Remove duplicate decorative announcements.
- [ ] Validate selected/disabled/toggle states.
- [ ] Validate chart/progress semantics.
- [ ] Perform bounded manual TalkBack pass where practical.
- [ ] Validate Light/Dark contrast and system bars.
- [ ] Run final whole-app Impeccable audit.
- [ ] Run final whole-repo Ponytail audit.
- [ ] Remove UI paths/styles/components made dead by the completed v1.3.x redesign cycle.
- [ ] Perform documentation sync for completed UI system.
- [ ] Pass release gate.

---

# D. Rules for audit findings discovered during development

## If the audit finds a P0

- Stop the current roadmap item.
- Fix before continuing.
- Re-run relevant focused and global verification.

## If the audit finds a P1 introduced by the current change

- Fix in the current version before marking the item complete.

## If the audit finds newly introduced dead code or over-engineering

- Remove/simplify it in the current version.
- Do not defer self-created debt.

## If the audit finds pre-existing P1 unrelated to the current change

- Report it immediately.
- Owner decides whether it interrupts the roadmap.

## If the audit finds pre-existing P2/P3

- Record it under a specific future version or `DEFER`.
- Do not silently enlarge scope.

## If the audit suggests a new abstraction

Before implementing, answer:

1. What current duplicated invariant does it own?
2. How many real consumers need it today?
3. Does it reduce conceptual complexity now?
4. Could a pure function or existing model solve the problem instead?
5. Is it being created for a hypothetical future feature?

If #3 is no or #5 is yes, do not add it.

---

# E. Definition of done for the roadmap cycle

The v1.3.4 cycle is complete only when:

- [ ] all v1.2.3–v1.3.4 roadmap items are either completed or explicitly re-scoped by the owner;
- [ ] no known P0/P1 release blocker remains;
- [ ] Room/Backup versions reflect only real persistence changes;
- [ ] global audits show no material newly accumulated dead code;
- [ ] canonical business rules remain centralized or equivalence-tested;
- [ ] Bingee retains a clear private-personal-tracker identity;
- [ ] major screens share a coherent visual language without unjustified uniformity;
- [ ] Details clearly communicates personal relationship state;
- [ ] Home prioritizes current personal activity;
- [ ] Collection clearly communicates its management scope;
- [ ] responsive behavior remains usable at narrow widths and large fonts;
- [ ] accessibility semantics are complete and non-redundant;
- [ ] canonical documentation matches the real codebase;
- [ ] generated audit reports remain outside canonical documentation;
- [ ] full verification is green.
