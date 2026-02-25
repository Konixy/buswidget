# BusWidget Agent Guide

This document is for coding agents working on this repository.

## What this project is

BusWidget is an iOS-first app for French public transport departures, currently scoped to **Rouen (Astuce network)**.

It has:

- A backend API (`apps/api`) that aggregates and normalizes GTFS + GTFS-RT feeds.
- A native SwiftUI iOS app (`apps/ios/BusWidget`) to search stops, favorite them, and view departures.
- A WidgetKit extension (`apps/ios/BusWidgetWidget`) showing departures for favorites.

## High-level architecture

1. **Upstream data**: Rouen Astuce GTFS static zip + GTFS-RT TripUpdates feeds.
2. **Backend**: Bun + Hono fetches/decode feeds and returns stable JSON to clients.
3. **iOS app**: Uses `APIClient` to call backend endpoints.
4. **Shared persistence**: Favorites stored in App Group UserDefaults.
5. **Widget**: Reads favorites from App Group and fetches departures from backend.

## Repo layout

- `apps/api`
  - `src/index.ts`: Hono routes, query validation, LAN URL startup logs
  - `src/env.ts`: environment parsing via Valibot
  - `src/lib/rouen-astuce.ts`: GTFS/GTFS-RT loading + parsing
  - `src/contracts.ts`: response schemas for runtime validation in tests
  - `src/*.test.ts`: unit/route tests + live API contract tests
- `apps/ios`
  - `project.yml`: source of truth for Xcode project generation
  - `BusWidget/`: SwiftUI app target
  - `BusWidgetWidget/`: WidgetKit extension target
  - `Shared/`: models, API client, favorites store, app-group config
- `README.md`: developer quickstart
- `apps/ios/README.md`: iOS-specific setup notes

## Core product behavior (current)

- Search Rouen stops from static GTFS stop list.
- Favorite/unfavorite stops in app.
- Show departures in stop detail screen.
- Widget can be configured per instance to choose a favorite stop.
- Widget refresh policy is timeline-based (`.after(...)`), not high-frequency polling.

## Backend details

### Stack

- Runtime: Bun
- HTTP: Hono
- Validation: Valibot
- GTFS static parsing: JSZip + csv-parse
- GTFS-RT decoding: gtfs-realtime-bindings

### Routes

- `GET /health`
- `GET /v1/rouen/stops/search?q=...&limit=...`
- `GET /v1/rouen/stops/:stopId/departures?limit=...&maxMinutes=...&lines=...` (`lines` optional, comma-separated)

### Important env vars

- `HOST` (default `0.0.0.0`)
- `PORT` (default `3000`)
- `ROUTEN_STATIC_GTFS_URL`
- `ROUTEN_TRIP_UPDATES_URLS` (comma-separated)
- `ROUTEN_STATIC_CACHE_TTL_MINUTES`

### Local network access (for physical iPhone)

Run:

```bash
bun run api:dev:lan
```

or:

```bash
bun --cwd apps/api run dev:lan
```

The API prints reachable LAN URLs. Use one as `API_BASE_URL` in iOS config.

## iOS details

### Project generation

**Never treat `BusWidget.xcodeproj` as source of truth.**

- Edit `apps/ios/project.yml`
- Regenerate with `bun run ios:generate`

### Targets

- `BusWidget` (application)
- `BusWidgetWidgetExtension` (widget extension)

Shared Swift files are in `apps/ios/Shared` and compiled into both targets.

### API base URL

Configured in `apps/ios/project.yml` as `API_BASE_URL` in both targets' Info properties.

- Simulator: usually `http://127.0.0.1:3000`
- Physical phone: use your machine LAN URL, then regenerate project

### Signing and app groups

- Team ID is currently set in `project.yml` (`DEVELOPMENT_TEAM: 5V374BVGUD`).
- `CODE_SIGN_STYLE` is `Automatic`.
- App Group: `group.com.buswidget.shared`.
- Keep the App Group value synchronized across:
  - `BusWidget/BusWidget.entitlements`
  - `BusWidgetWidget/BusWidgetWidget.entitlements`
  - `Shared/AppGroup.swift`
  - `project.yml`

## Known pitfalls and constraints

These caused real issues already; do not regress them.

1. **Do not add a standalone shared framework target unless fully needed.**
   - Previous `BusWidgetShared.framework` setup caused install issues (`Info.plist` missing).
   - Current architecture intentionally compiles `Shared` sources into both targets.

2. **Do not include plist/entitlement files as copied source resources.**
   - Causes `Multiple commands produce ... Info.plist` build errors.
   - `project.yml` source excludes already protect this; preserve them.

3. **Widget extension metadata on iOS 26**
   - For `com.apple.widgetkit-extension`, do not introduce unsupported extension keys.
   - Current generated widget Info.plist includes only `NSExtensionPointIdentifier` under `NSExtension`.

4. **Regeneration wipes manual Xcode edits.**
   - Always encode config in `project.yml`.

## Testing workflow

From repo root:

```bash
bun run check
bun run test
bun run test:real
```

Notes:

- `test`: fast unit/route tests.
- `test:real`: live integration tests against real Rouen feeds and response shape validation.

## Recommended workflow for agents

1. Make code changes.
2. Run backend checks/tests.
3. If iOS project config changed, run `bun run ios:generate`.
4. Update docs when behavior/config changes.

## Immediate roadmap context

Planned next steps (not yet implemented):

- Batch departures backend endpoint for widget efficiency.
- Better offline/stale-data UX in iOS.
- iOS CI once suitable Xcode 26 runners are available.
