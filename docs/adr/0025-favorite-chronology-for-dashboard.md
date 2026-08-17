# ADR 0025: Favorite chronology for dashboard ordering

- Status: Accepted
- Date: 2026-08-17

## Context

The Your Bingee dashboard must order Favorites by the time a title became a favorite. The existing `media_entries.is_favorite` flag retained no chronology, and Library `addedAt` is unrelated when a favorite is added independently of Library membership.

## Decision

Add nullable `media_entries.favorite_added_at` in Room v4. Set it only on a transition to favorite, clear it when removed, preserve it through metadata refresh and Library re-add, and carry it as optional `favoriteAddedAt` in Backup v1. Legacy rows and older backups remain null; UI uses a deterministic provider-identity tie-breaker without claiming a historical order.

## Consequences

New favorite actions have genuine ordering. Existing favorites cannot be historically reconstructed and appear after timestamped favorites in stable identity order until toggled again. No cross-provider or social semantics are introduced.
