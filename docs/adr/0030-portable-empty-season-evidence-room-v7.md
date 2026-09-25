# ADR 0030: Portable empty-season evidence (Room v7)

Status: Accepted
Date: 2026-09-13

Supersedes the empty-season coverage limitation in ADR 0029.

## Context

A positive declared episode count matched by stored rows proves coverage after an offline restore. Zero alone is ambiguous: an unfetched summary and a confirmed empty season look identical once restore drops cache timestamps. This caused a completed series with a genuinely empty regular season to become incomplete in Library and statistics (audit F02).

## Decision

- Persist `seasons.is_known_empty`, default false. A successful season episode write sets it only when the declared count is zero and no episode rows remain. A summary cannot establish this evidence. A positive count or changed provider identity invalidates it; a repeated zero summary preserves existing evidence.
- Room v7 adds the column through non-destructive migration 6 -> 7. Existing zero-count seasons inherit evidence only when they have a real fetch timestamp and no stored episodes. Existing timestamps and personal data are unchanged.
- Backup v2 adds optional boolean `isKnownEmpty` to each season. Missing fields in v1/v2 default to false. Validation rejects true with a positive count or any associated episode, before restore modifies the database.
- Export and transactional restore preserve this evidence separately from cache metadata. Restored `episodesFetchedAt` and language remain absent. Re-export after restore preserves the evidence again.
- Domain, Library, Continue Watching and completion-history SQL use the same coverage condition: exact stored/declaration count, and either a positive count or confirmed empty evidence. Specials still do not affect series completion; an entirely empty series is not complete.

## Compatibility and limits

The JSON addition is optional and remains within the v2 contract's additive-field policy. Older readers can ignore it, but cannot preserve this particular completion case. Backups created before this evidence existed cannot distinguish unknown from confirmed empty seasons; they remain conservative until a successful season refresh. No remote fetch or completion timestamp is fabricated.

## Verification

Regression coverage includes offline export/restore/re-export, legacy absence, malformed and contradictory evidence, summary invalidation, retained episode rows, domain derivation, migration 6 -> 7 and the complete migration chain.
