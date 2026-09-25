# ADR 0003: Start with a package-structured modular monolith

- Status: Accepted
- Date: 2026-07-31

## Context

The project needs clear boundaries but has no measured need for multiple Gradle modules. Early modularization would add build and ownership complexity before feature boundaries are proven.

## Decision

Keep one `app` Gradle module. Organize Kotlin packages under `core`, `data`, `domain`, `feature`, and `ui` as responsibilities appear. Milestone 0 creates only packages used by the application shell.

UI code must not call Retrofit services or Room DAOs directly. Repository boundaries and explicit mappers will be introduced with the features that require them.

## Consequences

- The build stays small and easy to navigate.
- Package boundaries provide an incremental path toward stronger separation.
- A future Gradle-module split requires measured build, ownership, or isolation benefits and a superseding ADR.
