# Bingee Roadmap

This document outlines the planned future enhancements and architecture revisions for Bingee following the `1.0.0-stable` release.

## Future Revisions

### 1. Anime Provider Revision (`1.1.0` Prerequisite)
- Re-evaluate Anime provider architecture, caching, and rate-limiting policies.
- Reinstate Anime catalogue UI (Search, Library filter, Details, Calendar, Notifications).
- Provider comparison and cross-provider linking UI for TMDB/Anime equivalences.

### 2. UI Refresh (`1.0.1-stable`)
- Refined Material 3 Expressive UI components and polished design system primitives.
- Enhanced micro-interactions and transitions across Details and Library screens.

### 3. Extended Import & Export Capabilities
- CSV export/import options for offline external reporting.
- Additional third-party service profile import support.

### 4. Multiplatform Preparedness & Future Shared Core
- Evaluation completed in Milestone 15 (ADR 0023).
- Backup v3 schema established as cross-platform data contract.
- Pure domain models (`core/model`) and contracts (`domain/repository`) ready for future KMP extraction once an iOS prototype is approved.

