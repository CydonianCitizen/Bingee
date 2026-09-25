# ADR 0007: Centralize current navigation without typed-route machinery

- Status: Accepted
- Date: 2026-08-01

## Context

The current graph has three argument-free top-level screens. Raw route strings must not spread, but typed-route infrastructure would add no present value.

## Decision

`TopLevelDestination` owns top-level route strings, order, labels, and icons. `BingeeNavHost` owns route registration and start destination. The application shell renders navigation state from these definitions, and reusable composables do not receive `NavController`.

Future argument-bearing routes must be defined centrally in `core/navigation`. Adopt typed routes only when arguments or graph complexity make them materially simpler.

## Consequences

- Route uniqueness, lookup, ordering, and selected state are deterministic and unit tested.
- Existing Home, Search, and Settings behavior stays unchanged.
- Destination metadata remains UI-aware by design; it is not a domain concept.
- Typed routes and detail-screen arguments remain deferred.
