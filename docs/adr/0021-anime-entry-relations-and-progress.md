# ADR 0021: Anime entries, relations, and entry-level progress

- Status: Superseded (Obsolete - Anime integration removed)
- Date: 2026-08-05

## Context

Jikan exposes one MyAnimeList anime identifier per catalogue entry, including sequels, prequels, split cours, remakes, movies, OVAs, ONAs, specials, and TV specials. These entries are not safely interchangeable with TMDB TV seasons or episodes.

## Decision

`MediaSource.JIKAN` plus the MAL anime ID string is one canonical Bingee entry and `MediaType.ANIME` is distinct from MOVIE and SERIES. Jikan format is provider metadata, never a replacement media type. Relations are navigable metadata only: they neither merge titles nor affect membership, rating, progress, backup identity, or events.

Milestone 13 stores no anime episode rows or history. It stores per-entry watched count, optional `completedAt`, and `updatedAt`. For a known total, zero is not started, a positive count below total is in progress, and count at/above total is completed; reducing below total clears completion. For unknown or ongoing totals, positive count is in progress and explicit completion sets `completedAt`; later count edits retain it until the user explicitly marks incomplete. Metadata refresh never lowers user progress. An anime whose provider format is MOVIE uses the same anime-progress row as a 0/1 watched control.

Personal ratings are Bingee-local integer 1--10 title ratings, independent of Jikan/MyAnimeList score. Library removal removes only membership; metadata, relations, progress, rating, and provider identity remain for offline re-add.

## Consequences

Every relation can be opened as a separate Jikan detail route without accidental loss or merging of personal state. A future cross-provider or cross-entry link must be user-controlled and reversible. Anime premiere events derive only from a reliable start `LocalDate`; episode schedules and anime notifications are intentionally absent.

## Official references verified 2026-08-05

- Jikan: [REST API v4](https://docs.api.jikan.moe/) documents MyAnimeList-derived anime fields, nullable unknown values, ISO-8601 dates, read-only access, and separate `/anime/{id}/full` relations.
