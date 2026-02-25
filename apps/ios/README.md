# BusWidget iOS app

Native SwiftUI app + WidgetKit extension for Rouen departures.

## Targets

- `BusWidget`: iOS app (search stops, save favorites, inspect departures)
- `BusWidgetWidgetExtension`: home screen widget using shared favorites

Shared code lives in `apps/ios/Shared` and is compiled into both targets.

## iOS 26 adaptation

This project is configured with deployment target `iOS 26.0` in `project.yml`.

The app uses current SwiftUI/WidgetKit patterns suitable for modern iOS:

- `NavigationStack` + `ContentUnavailableView` for app UI states
- Widget timeline provider with explicit refresh policy (`.after(...)`)
- Shared data through App Groups (`group.com.buswidget.shared`)
- Widget background using `containerBackground(..., for: .widget)`

## Prerequisites

- Xcode version that supports iOS 26 SDK
- `xcodegen` installed (`brew install xcodegen`)
- Backend running locally (`bun run dev` from repo root)

## Generate project

```bash
cd apps/ios
xcodegen generate
open BusWidget.xcodeproj
```

## Backend URL

Default API base URL is `http://127.0.0.1:3000` and is injected via `Info.plist` build setting.

If you run on a physical device:

1. Start API with LAN binding: `bun --cwd apps/api run dev:lan`
2. Copy the LAN URL printed by the API logs (`http://192.168.x.x:3000`)
3. Replace `API_BASE_URL` in `project.yml` for both app and widget targets
4. Regenerate the project (`bun run ios:generate`) and run again

## App Group and signing

Before running on device:

1. Set your Apple Team in Xcode project signing settings.
2. Enable App Group capability for both app and widget targets.
3. Ensure `group.com.buswidget.shared` exists (or replace it consistently in:
   - `BusWidget/BusWidget.entitlements`
   - `BusWidgetWidget/BusWidgetWidget.entitlements`
   - `Shared/AppGroup.swift`
   - `project.yml`)

## Notes

- Widget can be configured per instance to show departures for a selected favorite stop.
- Widget refresh is timeline-driven and not guaranteed to run every minute.
