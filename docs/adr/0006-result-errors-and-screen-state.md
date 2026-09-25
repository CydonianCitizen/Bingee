# ADR 0006: Use structured results, safe UI errors, and screen-specific state

- Status: Accepted
- Date: 2026-08-01

## Context

Infrastructure failures must cross repository boundaries without leaking HTTP, database, exception, or user-facing text concerns into domain code. Screens also need explicit state that remains testable and understandable.

## Decision

Use `AppResult.Success(value)` and `AppResult.Failure(AppError)` for fallible repository operations. `AppError` is a small closed set of application categories with deterministic retryability and no raw `Throwable` or infrastructure types.

Map `AppError` to a string-resource ID and retry capability in `core/ui`. Internal exception messages, URLs, secrets, and stack traces cannot become user text through this mapping.

Each stateful screen defines an immutable, screen-specific `UiState`. ViewModels keep `MutableStateFlow` private, expose read-only `StateFlow`, own coroutines through `viewModelScope`, and receive events through explicit methods. One-off effects are introduced only for real transient behavior.

## Consequences

- Error classification can be tested without Android framework exceptions.
- UI wording can change without changing domain contracts.
- State variants describe screen meaning instead of hiding it behind a universal generic wrapper.
- `Unknown` is provisionally non-retryable; provider integrations may supersede that policy with evidence.
- Provider exception translation and any one-off effect mechanism remain deferred.
