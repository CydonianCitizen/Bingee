# ADR 0032: Recoverable notification claims in Room v9

Status: Accepted
Date: 2026-09-24

## Context

The notification worker previously published before writing the delivery ledger. A Room error after publication, or two workers reading the absent ledger row together, could publish the same event more than once.

## Decision

- Room v9 adds nullable claim token and expiry columns to `notification_deliveries`. Existing delivered rows remain delivered with null claims.
- A worker atomically inserts a pending row before calling Android's notification API. Only one worker can own the composite identity. A worker can atomically take an expired claim after five minutes, including after process death.
- A successful post completes the row only with its matching token. A failed post releases that token. Pending rows do not count as delivered. Portable restore continues to clear the technical ledger.
- Delivery is at least once when publication succeeds but completion fails, or the process dies between those operations. Android's notification API and Room cannot commit in one transaction; an expired claim can therefore publish again. The deterministic notification ID limits duplicates while a notification remains visible but does not guarantee uniqueness after dismissal.

## Consequences

Immediate retries and concurrent workers do not publish a claimed event twice. Crashed workers can be recovered. The rare ambiguous outcome after publication can still publish again, and the product documentation states this limit explicitly.

## Verification

JVM tests cover publication, completion failure, and immediate retry. Room tests cover competing claims, token ownership, reopening a persisted claim after process recreation, lease expiry, and the 8 to 9 migration preserving delivered rows.
