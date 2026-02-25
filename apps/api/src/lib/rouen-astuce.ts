import { parse } from "csv-parse/sync";
import GtfsRealtimeBindings from "gtfs-realtime-bindings";
import JSZip from "jszip";

export type StopInfo = {
  id: string;
  name: string;
  lat: number | null;
  lon: number | null;
  stopCode: string | null;
  locationType: number | null;
  parentStationId: string | null;
  transportModes: string[];
  lineHints: string[];
};

type RouteInfo = {
  id: string;
  shortName: string;
  longName: string;
  type: number | null;
};

type TripInfo = {
  id: string;
  routeId: string;
  headsign: string;
};

type StaticGtfsData = {
  fetchedAtUnix: number;
  stopsById: Map<string, StopInfo>;
  routesById: Map<string, RouteInfo>;
  tripsById: Map<string, TripInfo>;
  childrenByParentId: Map<string, string[]>;
};

export type Departure = {
  stopId: string;
  stopName: string;
  routeId: string;
  line: string;
  destination: string;
  departureUnix: number;
  departureIso: string;
  minutesUntilDeparture: number;
  sourceUrl: string;
};

type Cache = {
  data: StaticGtfsData | null;
  expiresAtUnix: number;
  loadingPromise: Promise<StaticGtfsData> | null;
};

const staticCache: Cache = {
  data: null,
  expiresAtUnix: 0,
  loadingPromise: null,
};

const DEFAULT_STRING = "Unknown";
const TEOR_LINE_RE = /^T\d+$/i;
const ROUTE_MODE_PRIORITY = ["Metro", "Tram", "TEOR", "Bus", "Train", "Ferry"];
const PROVIDER_PRIORITY: Record<string, number> = {
  TCAR: 0,
  TNI: 1,
  TAE: 2,
};

const getField = (row: Record<string, string>, key: string): string => {
  return row[key] ?? "";
};

const parseCsvRecords = (text: string): Record<string, string>[] => {
  const firstLine = text.split("\n", 1)[0] ?? "";
  const delimiter = firstLine.includes(";") ? ";" : ",";

  const rows = parse(text, {
    columns: true,
    skip_empty_lines: true,
    delimiter,
    bom: true,
  }) as Record<string, string>[];
  return rows;
};

const fromLongLike = (value: unknown): number | null => {
  if (typeof value === "number") {
    return value;
  }
  if (typeof value === "string") {
    const numeric = Number(value);
    return Number.isFinite(numeric) ? numeric : null;
  }
  if (typeof value === "object" && value !== null) {
    const maybeLong = value as { toNumber?: () => number };
    if (typeof maybeLong.toNumber === "function") {
      return maybeLong.toNumber();
    }
  }
  return null;
};

const toRouteInfo = (row: Record<string, string>): RouteInfo => ({
  id: getField(row, "route_id"),
  shortName: getField(row, "route_short_name") || getField(row, "route_id"),
  longName:
    getField(row, "route_long_name") ||
    getField(row, "route_short_name") ||
    getField(row, "route_id"),
  type: getField(row, "route_type") ? Number(getField(row, "route_type")) : null,
});

const toStopInfo = (row: Record<string, string>): StopInfo => ({
  id: getField(row, "stop_id"),
  name: getField(row, "stop_name") || getField(row, "stop_id"),
  lat: getField(row, "stop_lat") ? Number(getField(row, "stop_lat")) : null,
  lon: getField(row, "stop_lon") ? Number(getField(row, "stop_lon")) : null,
  stopCode: getField(row, "stop_code") || null,
  locationType: getField(row, "location_type")
    ? Number(getField(row, "location_type"))
    : null,
  parentStationId: getField(row, "parent_station") || null,
  transportModes: [],
  lineHints: [],
});

const toTripInfo = (row: Record<string, string>): TripInfo => ({
  id: getField(row, "trip_id"),
  routeId: getField(row, "route_id"),
  headsign: getField(row, "trip_headsign"),
});

const normalizeForSearch = (value: string) =>
  value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase();

const getStopProvider = (stopId: string): string => {
  const [provider] = stopId.split(":", 1);
  return provider ?? "";
};

const getProviderPriority = (stopId: string): number => {
  const provider = getStopProvider(stopId);
  return PROVIDER_PRIORITY[provider] ?? 99;
};

const toTransportMode = (route: RouteInfo): string | null => {
  if (TEOR_LINE_RE.test(route.shortName.trim())) {
    return "TEOR";
  }

  switch (route.type) {
    case 0:
      return "Tram";
    case 1:
      return "Metro";
    case 2:
      return "Train";
    case 3:
      return "Bus";
    case 4:
      return "Ferry";
    default:
      return null;
  }
};

const getRouteIdsForStop = (
  stop: StopInfo,
  routeIdsByStopId: Map<string, Set<string>>,
  childrenByParentId: Map<string, string[]>,
) => {
  const routeIds = new Set(routeIdsByStopId.get(stop.id) ?? []);

  if (stop.locationType === 1) {
    for (const childId of childrenByParentId.get(stop.id) ?? []) {
      for (const routeId of routeIdsByStopId.get(childId) ?? []) {
        routeIds.add(routeId);
      }
    }
  }

  return routeIds;
};

const toSortedTransportModes = (
  routeIds: Set<string>,
  routesById: Map<string, RouteInfo>,
) => {
  const modes = new Set<string>();
  for (const routeId of routeIds) {
    const route = routesById.get(routeId);
    if (!route) {
      continue;
    }

    const mode = toTransportMode(route);
    if (mode) {
      modes.add(mode);
    }
  }

  return Array.from(modes).sort(
    (a, b) => ROUTE_MODE_PRIORITY.indexOf(a) - ROUTE_MODE_PRIORITY.indexOf(b),
  );
};

const toSortedLineHints = (
  routeIds: Set<string>,
  routesById: Map<string, RouteInfo>,
) => {
  const lines = new Set<string>();
  for (const routeId of routeIds) {
    const route = routesById.get(routeId);
    const line = route?.shortName?.trim();
    if (line) {
      lines.add(line);
    }
  }

  return Array.from(lines).sort((a, b) =>
    a.localeCompare(b, "fr", { sensitivity: "base", numeric: true }),
  );
};

const parseGtfsZip = async (zipBuffer: ArrayBuffer): Promise<StaticGtfsData> => {
  const zip = await JSZip.loadAsync(zipBuffer);
  const [stopsText, routesText, tripsText, stopTimesText] = await Promise.all([
    zip.file("stops.txt")?.async("text"),
    zip.file("routes.txt")?.async("text"),
    zip.file("trips.txt")?.async("text"),
    zip.file("stop_times.txt")?.async("text"),
  ]);

  if (!stopsText || !routesText || !tripsText || !stopTimesText) {
    throw new Error("Missing required GTFS files in Rouen static feed");
  }

  const stopsById = new Map<string, StopInfo>();
  const routesById = new Map<string, RouteInfo>();
  const tripsById = new Map<string, TripInfo>();
  const childrenByParentId = new Map<string, string[]>();
  const routeIdsByStopId = new Map<string, Set<string>>();

  for (const row of parseCsvRecords(stopsText)) {
    if (!row.stop_id) {
      continue;
    }

    const stop = toStopInfo(row);
    stopsById.set(stop.id, stop);

    if (stop.parentStationId) {
      const children = childrenByParentId.get(stop.parentStationId) ?? [];
      children.push(stop.id);
      childrenByParentId.set(stop.parentStationId, children);
    }
  }

  for (const row of parseCsvRecords(routesText)) {
    if (!row.route_id) {
      continue;
    }
    routesById.set(row.route_id, toRouteInfo(row));
  }

  for (const row of parseCsvRecords(tripsText)) {
    if (!row.trip_id || !row.route_id) {
      continue;
    }
    tripsById.set(row.trip_id, toTripInfo(row));
  }

  for (const row of parseCsvRecords(stopTimesText)) {
    const tripId = getField(row, "trip_id");
    const stopId = getField(row, "stop_id");
    if (!tripId || !stopId) {
      continue;
    }

    const routeId = tripsById.get(tripId)?.routeId;
    if (!routeId) {
      continue;
    }

    const existing = routeIdsByStopId.get(stopId) ?? new Set<string>();
    existing.add(routeId);
    routeIdsByStopId.set(stopId, existing);
  }

  for (const stop of stopsById.values()) {
    const routeIds = getRouteIdsForStop(
      stop,
      routeIdsByStopId,
      childrenByParentId,
    );
    stop.transportModes = toSortedTransportModes(routeIds, routesById);
    stop.lineHints = toSortedLineHints(routeIds, routesById);
  }

  return {
    fetchedAtUnix: Math.floor(Date.now() / 1000),
    stopsById,
    routesById,
    tripsById,
    childrenByParentId,
  };
};

export const loadRouenStaticData = async (
  staticGtfsUrl: string,
  ttlMinutes: number,
): Promise<StaticGtfsData> => {
  const nowUnix = Math.floor(Date.now() / 1000);

  if (staticCache.data && nowUnix < staticCache.expiresAtUnix) {
    return staticCache.data;
  }

  if (staticCache.loadingPromise) {
    return staticCache.loadingPromise;
  }

  staticCache.loadingPromise = (async () => {
    try {
      const response = await fetch(staticGtfsUrl);
      if (!response.ok) {
        throw new Error(
          `Failed to download Rouen static GTFS: ${response.status}`,
        );
      }

      const zipBuffer = await response.arrayBuffer();
      const data = await parseGtfsZip(zipBuffer);

      staticCache.data = data;
      staticCache.expiresAtUnix = nowUnix + ttlMinutes * 60;

      return data;
    } finally {
      staticCache.loadingPromise = null;
    }
  })();

  return staticCache.loadingPromise;
};

const getDepartureUnix = (stopTimeUpdate: Record<string, unknown>): number | null => {
  const arrival = stopTimeUpdate.arrival as Record<string, unknown> | undefined;
  const departure = stopTimeUpdate.departure as Record<string, unknown> | undefined;

  return fromLongLike(arrival?.time) ?? fromLongLike(departure?.time);
};

const decodeTripUpdates = (buffer: ArrayBuffer) => {
  const uint8Array = new Uint8Array(buffer);
  return GtfsRealtimeBindings.transit_realtime.FeedMessage.decode(uint8Array);
};

export const getRouenDeparturesForStop = async (args: {
  stopId: string;
  maxMinutesAhead: number;
  limit: number;
  staticGtfsUrl: string;
  staticCacheTtlMinutes: number;
  tripUpdatesUrls: string[];
}) => {
  const nowUnix = Math.floor(Date.now() / 1000);
  const maxUnix = nowUnix + args.maxMinutesAhead * 60;

  const staticData = await loadRouenStaticData(
    args.staticGtfsUrl,
    args.staticCacheTtlMinutes,
  );

  const requestedStop = staticData.stopsById.get(args.stopId) ?? null;

  const tripsByUrl = await Promise.all(
    args.tripUpdatesUrls.map(async (sourceUrl) => {
      const response = await fetch(sourceUrl);
      if (!response.ok) {
        throw new Error(`Failed GTFS-RT trip updates fetch: ${response.status}`);
      }
      const buffer = await response.arrayBuffer();
      const message = decodeTripUpdates(buffer);
      return { sourceUrl, message };
    }),
  );

  const collectDepartures = (targetStopIds: Set<string>) => {
    const departures: Departure[] = [];
    const seenDepartures = new Set<string>();

    for (const feed of tripsByUrl) {
      for (const entity of feed.message.entity) {
        const tripUpdate = entity.tripUpdate;
        if (!tripUpdate) {
          continue;
        }

        const tripRouteId = tripUpdate.trip?.routeId ?? "";
        const tripId = tripUpdate.trip?.tripId ?? "";
        const routeId = tripRouteId || staticData.tripsById.get(tripId)?.routeId || "";
        const fallbackHeadsign = staticData.tripsById.get(tripId)?.headsign ?? "";

        const stopTimeUpdates = tripUpdate.stopTimeUpdate ?? [];

        for (const stopTimeUpdate of stopTimeUpdates) {
          const stopId = stopTimeUpdate.stopId;
          if (!stopId || !targetStopIds.has(stopId)) {
            continue;
          }

          const departureUnix = getDepartureUnix(
            stopTimeUpdate as unknown as Record<string, unknown>,
          );
          if (!departureUnix || departureUnix < nowUnix || departureUnix > maxUnix) {
            continue;
          }

          const dedupeKey = `${feed.sourceUrl}|${routeId}|${tripId}|${stopId}|${departureUnix}`;
          if (seenDepartures.has(dedupeKey)) {
            continue;
          }
          seenDepartures.add(dedupeKey);

          const routeInfo = staticData.routesById.get(routeId);
          const stopInfo = staticData.stopsById.get(stopId) ?? requestedStop;
          const stopTimeRecord = stopTimeUpdate as unknown as Record<string, unknown>;
          const stopHeadsign =
            typeof stopTimeRecord.stopHeadsign === "string"
              ? stopTimeRecord.stopHeadsign
              : "";

          departures.push({
            stopId,
            stopName: stopInfo?.name ?? DEFAULT_STRING,
            routeId,
            line: routeInfo?.shortName || routeId || DEFAULT_STRING,
            destination:
              stopHeadsign || fallbackHeadsign || routeInfo?.longName || DEFAULT_STRING,
            departureUnix,
            departureIso: new Date(departureUnix * 1000).toISOString(),
            minutesUntilDeparture: Math.max(
              0,
              Math.round((departureUnix - nowUnix) / 60),
            ),
            sourceUrl: feed.sourceUrl,
          });
        }
      }
    }

    departures.sort((a, b) => a.departureUnix - b.departureUnix);
    return departures;
  };

  const primaryTargetStopIds = new Set<string>([args.stopId]);
  if (requestedStop?.locationType === 1) {
    for (const childStopId of staticData.childrenByParentId.get(requestedStop.id) ?? []) {
      primaryTargetStopIds.add(childStopId);
    }
  }

  let departures = collectDepartures(primaryTargetStopIds);

  if (
    departures.length === 0 &&
    requestedStop?.locationType !== 1 &&
    requestedStop?.parentStationId
  ) {
    const siblingTargetStopIds = new Set<string>([args.stopId]);
    for (const siblingStopId of staticData.childrenByParentId.get(
      requestedStop.parentStationId,
    ) ?? []) {
      siblingTargetStopIds.add(siblingStopId);
    }

    departures = collectDepartures(siblingTargetStopIds);
  }

  let latestFeedTimestamp = 0;

  for (const feed of tripsByUrl) {
    const headerTimestamp = fromLongLike(feed.message.header.timestamp);
    if (headerTimestamp && headerTimestamp > latestFeedTimestamp) {
      latestFeedTimestamp = headerTimestamp;
    }
  }

  return {
    generatedAtUnix: nowUnix,
    feedTimestampUnix: latestFeedTimestamp,
    stop: requestedStop,
    departures: departures.slice(0, args.limit),
  };
};

export const searchRouenStops = async (args: {
  query: string;
  limit: number;
  staticGtfsUrl: string;
  staticCacheTtlMinutes: number;
}) => {
  const staticData = await loadRouenStaticData(
    args.staticGtfsUrl,
    args.staticCacheTtlMinutes,
  );

  const q = normalizeForSearch(args.query.trim());
  const rankedStops: {
    stop: StopInfo;
    startsWithName: boolean;
    startsWithCode: boolean;
    hasKnownService: boolean;
    isBoardingStop: boolean;
    providerPriority: number;
    lineHintCount: number;
  }[] = [];

  for (const stop of staticData.stopsById.values()) {
    const normalizedName = normalizeForSearch(stop.name);
    const normalizedId = normalizeForSearch(stop.id);
    const normalizedCode = normalizeForSearch(stop.stopCode ?? "");

    const isMatch =
      normalizedName.includes(q) ||
      normalizedId.includes(q) ||
      normalizedCode.includes(q);
    if (!isMatch) {
      continue;
    }

    rankedStops.push({
      stop,
      startsWithName: normalizedName.startsWith(q),
      startsWithCode: normalizedCode.startsWith(q),
      hasKnownService: stop.transportModes.length > 0,
      isBoardingStop: stop.locationType !== 1,
      providerPriority: getProviderPriority(stop.id),
      lineHintCount: stop.lineHints.length,
    });
  }

  rankedStops.sort((a, b) => {
    if (a.startsWithName !== b.startsWithName) {
      return a.startsWithName ? -1 : 1;
    }

    if (a.hasKnownService !== b.hasKnownService) {
      return a.hasKnownService ? -1 : 1;
    }

    if (a.isBoardingStop !== b.isBoardingStop) {
      return a.isBoardingStop ? -1 : 1;
    }

    if (a.providerPriority !== b.providerPriority) {
      return a.providerPriority - b.providerPriority;
    }

    if (a.lineHintCount !== b.lineHintCount) {
      return b.lineHintCount - a.lineHintCount;
    }

    if (a.startsWithCode !== b.startsWithCode) {
      return a.startsWithCode ? -1 : 1;
    }

    const nameCmp = a.stop.name.localeCompare(b.stop.name, "fr", {
      sensitivity: "base",
      numeric: true,
    });
    if (nameCmp !== 0) {
      return nameCmp;
    }

    return a.stop.id.localeCompare(b.stop.id, "fr", {
      sensitivity: "base",
      numeric: true,
    });
  });

  const results = rankedStops.slice(0, args.limit).map((entry) => entry.stop);

  return {
    count: results.length,
    results,
  };
};
