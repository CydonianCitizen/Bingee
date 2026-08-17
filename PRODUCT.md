# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

Inferred from the product brief: one person tracking a private, personal media history on an Android device.

## Product Purpose

Inferred from the product brief: Bingee lets a person search films and series, save them locally, track regular episodes and films as watched, keep favorites, follow releases, and export or restore their data without an account or proprietary backend.

## Positioning

The user's viewing history remains useful offline and portable because local persistence is the product boundary, not a sign-in service.

## Operating Context

The primary workflow is offline-capable Android use with optional TMDB metadata refresh. Profile is the personal dashboard and the existing Profile/Library route is the collection destination.

## Capabilities and Constraints

TMDB is the current provider. Serial progress excludes specials and Season 0. Watching is derived from current regular episode availability and progress; caught-up is not persisted. No social, recommendation, account, cloud-sync, or anime features are in scope for this milestone.

## Brand Commitments

Bingee name and existing Material 3 ColorScheme, light/dark themes, edge-to-edge shell, and existing poster/image loading remain authoritative. Product language distinguishes private personal history from social identity.

## Evidence on Hand

Repository code, Room schema, existing Profile/Library, Details, Search, Settings, and shared poster components. No user accounts or backend are present.

## Product Principles

- User data stays local and exportable.
- Derived serial state follows current regular episode availability.
- Existing navigation and canonical repository logic are reused.
- UI stays accessible, scannable, and calm.

## Accessibility & Inclusion

Inferred from the project instructions: Material touch targets, content descriptions, string resources, font scaling, loading/error/empty states, and light/dark parity are required.
