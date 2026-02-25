# buswidget monorepo

Monorepo for an iOS public transport widget project, starting with Rouen (Astuce network).

## Stack

- Monorepo: Turbo + Bun workspaces
- Backend: Bun + Hono + TypeScript
- Mobile app: Native iOS Swift (to be added)

## Rouen data source research

Primary source comes from `transport.data.gouv.fr` dataset:

- Dataset: `Réseau urbain Astuce`
- Dataset page: <https://transport.data.gouv.fr/datasets/donnees-statiques-et-temps-reel-du-reseau-astuce-metropole-rouen-normandie>

Relevant endpoints used now:

- Static GTFS zip (stops, routes, trips):
  - `https://api.mrn.cityway.fr/dataflow/offre-tc/download?provider=ASTUCE&dataFormat=gtfs&dataProfil=ASTUCE`
- GTFS-RT Trip Updates (real-time departures):
  - `https://api.mrn.cityway.fr/dataflow/horaire-tc-tr/download?provider=TCAR&dataFormat=gtfs-rt`
  - `https://api.mrn.cityway.fr/dataflow/horaire-tc-tr/download?provider=TNI&dataFormat=gtfs-rt`
  - `https://api.mrn.cityway.fr/dataflow/horaire-tc-tr/download?provider=TAE&dataFormat=gtfs-rt`

These feeds are protobuf GTFS-RT (`application/x-protobuf` style binary data), so the backend decodes them server-side and returns JSON to the app/widget.

## Project layout

- `apps/api`: Hono backend for Rouen stop search and departures
- `apps/ios`: native SwiftUI app + WidgetKit project (generated with XcodeGen)
- `packages/config`: shared config placeholders

## Run locally

```bash
bun install
bun run dev
```

The API starts from `apps/api/src/index.ts`.

For real device testing on the same Wi-Fi network:

```bash
bun --cwd apps/api run dev:lan
```

The API will bind to `0.0.0.0` and print LAN URLs (for example `http://192.168.x.x:3000`) that you can use from your iPhone.

## Current API endpoints

- `GET /health`
- `GET /v1/rouen/stops/search?q=theatre&limit=10`
- `GET /v1/rouen/stops/:stopId/departures?limit=8&maxMinutes=90&lines=T2,F` (`lines` optional, comma-separated)

## Tests

- Fast unit/route tests:
  - `bun run test`
- Live integration tests against real Rouen APIs and response shape checks:
  - `bun run test:real`

## Next implementation steps

- Add iOS widget configuration (select specific favorite stop)
- Add backend endpoint for batch departures (optimize widget refresh)
- Add offline cache and stale-data UI on iOS
- Add CI step for iOS lint/tests once Xcode 26 runners are available
