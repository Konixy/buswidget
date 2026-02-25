import { parse } from "csv-parse/sync";
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
  serviceId: string;
};

type StopTimeInfo = {
  tripId: string;
  stopId: string;
  departureSeconds: number;
  stopHeadsign: string;
};

type ServiceCalendarInfo = {
  startDate: number;
  endDate: number;
  activeWeekdays: [boolean, boolean, boolean, boolean, boolean, boolean, boolean];
};

type StaticGtfsData = {
  fetchedAtUnix: number;
  stopsById: Map<string, StopInfo>;
  routesById: Map<string, RouteInfo>;
  tripsById: Map<string, TripInfo>;
  childrenByParentId: Map<string, string[]>;
  stopTimesByStopId: Map<string, StopTimeInfo[]>;
  serviceCalendarsById: Map<string, ServiceCalendarInfo>;
  serviceExceptionsByDate: Map<number, Map<string, 1 | 2>>;
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
  isRealtime: boolean;
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
  longName: getField(row, "route_long_name") || getField(row, "route_short_name") || getField(row, "route_id"),
  type: getField(row, "route_type") ? Number(getField(row, "route_type")) : null,
});

const toStopInfo = (row: Record<string, string>): StopInfo => ({
  id: getField(row, "stop_id"),
  name: getField(row, "stop_name") || getField(row, "stop_id"),
  lat: getField(row, "stop_lat") ? Number(getField(row, "stop_lat")) : null,
  lon: getField(row, "stop_lon") ? Number(getField(row, "stop_lon")) : null,
  stopCode: getField(row, "stop_code") || null,
  locationType: getField(row, "location_type") ? Number(getField(row, "location_type")) : null,
  parentStationId: getField(row, "parent_station") || null,
  transportModes: [],
  lineHints: [],
});

const toTripInfo = (row: Record<string, string>): TripInfo => ({
  id: getField(row, "trip_id"),
  routeId: getField(row, "route_id"),
  headsign: getField(row, "trip_headsign"),
  serviceId: getField(row, "service_id"),
});

const parseGtfsTimeToSeconds = (value: string): number | null => {
  const [hoursText, minutesText, secondsText] = value.split(":");
  if (hoursText === undefined || minutesText === undefined || secondsText === undefined) {
    return null;
  }
  const hours = Number(hoursText);
  const minutes = Number(minutesText);
  const seconds = Number(secondsText);
  if (
    !Number.isFinite(hours) ||
    !Number.isFinite(minutes) ||
    !Number.isFinite(seconds) ||
    minutes < 0 ||
    minutes > 59 ||
    seconds < 0 ||
    seconds > 59 ||
    hours < 0
  ) {
    return null;
  }
  return hours * 3600 + minutes * 60 + seconds;
};

const parseGtfsDateKey = (value: string): number | null => {
  if (!/^\d{8}$/.test(value)) {
    return null;
  }
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric : null;
};

const toWeekdayFlag = (value: string) => value === "1";

const toServiceCalendarInfo = (row: Record<string, string>): ServiceCalendarInfo | null => {
  const startDate = parseGtfsDateKey(getField(row, "start_date"));
  const endDate = parseGtfsDateKey(getField(row, "end_date"));
  if (!startDate || !endDate) {
    return null;
  }
  return {
    startDate,
    endDate,
    activeWeekdays: [
      toWeekdayFlag(getField(row, "monday")),
      toWeekdayFlag(getField(row, "tuesday")),
      toWeekdayFlag(getField(row, "wednesday")),
      toWeekdayFlag(getField(row, "thursday")),
      toWeekdayFlag(getField(row, "friday")),
      toWeekdayFlag(getField(row, "saturday")),
      toWeekdayFlag(getField(row, "sunday")),
    ],
  };
};

const PARIS_TIME_ZONE = "Europe/Paris";

const PARIS_DATE_FORMATTER = new Intl.DateTimeFormat("en-CA", {
  timeZone: PARIS_TIME_ZONE,
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
});

const PARIS_WEEKDAY_FORMATTER = new Intl.DateTimeFormat("en-US", {
  timeZone: PARIS_TIME_ZONE,
  weekday: "short",
});

const PARIS_OFFSET_FORMATTER = new Intl.DateTimeFormat("en-US", {
  timeZone: PARIS_TIME_ZONE,
  timeZoneName: "shortOffset",
  hour: "2-digit",
  minute: "2-digit",
  hour12: false,
});

const toParisDateKey = (unixSeconds: number): number => {
  const date = new Date(unixSeconds * 1000);
  const parts = PARIS_DATE_FORMATTER.formatToParts(date);
  const year = Number(parts.find((part) => part.type === "year")?.value ?? "0");
  const month = Number(parts.find((part) => part.type === "month")?.value ?? "0");
  const day = Number(parts.find((part) => part.type === "day")?.value ?? "0");
  return year * 10000 + month * 100 + day;
};

const getParisWeekdayIndex = (dateKey: number): number => {
  const year = Math.floor(dateKey / 10000);
  const month = Math.floor((dateKey % 10000) / 100);
  const day = dateKey % 100;
  const sampleDate = new Date(Date.UTC(year, month - 1, day, 12, 0, 0));
  const weekdayShort = PARIS_WEEKDAY_FORMATTER.format(sampleDate);
  switch (weekdayShort) {
    case "Mon":
      return 0;
    case "Tue":
      return 1;
    case "Wed":
      return 2;
    case "Thu":
      return 3;
    case "Fri":
      return 4;
    case "Sat":
      return 5;
    case "Sun":
      return 6;
    default:
      return 0;
  }
};

const parseOffsetSeconds = (offset: string): number => {
  const match = offset.match(/^GMT([+-])(\d{1,2})(?::?(\d{2}))?$/);
  if (!match) {
    return 0;
  }
  const sign = match[1] === "-" ? -1 : 1;
  const hours = Number(match[2] ?? "0");
  const minutes = Number(match[3] ?? "0");
  return sign * (hours * 3600 + minutes * 60);
};

const getParisOffsetSecondsAtUnix = (unixSeconds: number): number => {
  const parts = PARIS_OFFSET_FORMATTER.formatToParts(new Date(unixSeconds * 1000));
  const value = parts.find((part) => part.type === "timeZoneName")?.value ?? "GMT+0";
  return parseOffsetSeconds(value);
};

const toUnixFromParisDateAndSeconds = (dateKey: number, secondsFromMidnight: number): number => {
  const year = Math.floor(dateKey / 10000);
  const month = Math.floor((dateKey % 10000) / 100);
  const day = dateKey % 100;
  const utcMidnightUnix = Date.UTC(year, month - 1, day, 0, 0, 0) / 1000;
  let offset = getParisOffsetSecondsAtUnix(utcMidnightUnix);
  let unix = utcMidnightUnix - offset + secondsFromMidnight;
  const refinedOffset = getParisOffsetSecondsAtUnix(unix);
  if (refinedOffset !== offset) {
    offset = refinedOffset;
    unix = utcMidnightUnix - offset + secondsFromMidnight;
  }
  return unix;
};

const getCandidateServiceDateKeys = (nowUnix: number, maxUnix: number): number[] => {
  const keys = new Set<number>([toParisDateKey(nowUnix - 86400), toParisDateKey(nowUnix), toParisDateKey(maxUnix), toParisDateKey(maxUnix + 86400)]);
  return Array.from(keys).sort((a, b) => a - b);
};

const normalizeForSearch = (value: string) =>
  value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase();

const normalizeLineName = (value: string) => value.trim().toLocaleUpperCase("fr-FR");

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

const getRouteIdsForStop = (stop: StopInfo, routeIdsByStopId: Map<string, Set<string>>, childrenByParentId: Map<string, string[]>) => {
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

const toSortedTransportModes = (routeIds: Set<string>, routesById: Map<string, RouteInfo>) => {
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

  return Array.from(modes).sort((a, b) => ROUTE_MODE_PRIORITY.indexOf(a) - ROUTE_MODE_PRIORITY.indexOf(b));
};

const toSortedLineHints = (routeIds: Set<string>, routesById: Map<string, RouteInfo>) => {
  const lines = new Set<string>();
  for (const routeId of routeIds) {
    const route = routesById.get(routeId);
    const line = route?.shortName?.trim();
    if (line) {
      lines.add(line);
    }
  }

  return Array.from(lines).sort((a, b) => a.localeCompare(b, "fr", { sensitivity: "base", numeric: true }));
};

const parseGtfsZip = async (zipBuffer: ArrayBuffer): Promise<StaticGtfsData> => {
  const zip = await JSZip.loadAsync(zipBuffer);
  const [stopsText, routesText, tripsText, stopTimesText, calendarText, calendarDatesText] = await Promise.all([
    zip.file("stops.txt")?.async("text"),
    zip.file("routes.txt")?.async("text"),
    zip.file("trips.txt")?.async("text"),
    zip.file("stop_times.txt")?.async("text"),
    zip.file("calendar.txt")?.async("text"),
    zip.file("calendar_dates.txt")?.async("text"),
  ]);

  if (!stopsText || !routesText || !tripsText || !stopTimesText) {
    throw new Error("Missing required GTFS files in Rouen static feed");
  }

  const stopsById = new Map<string, StopInfo>();
  const routesById = new Map<string, RouteInfo>();
  const tripsById = new Map<string, TripInfo>();
  const childrenByParentId = new Map<string, string[]>();
  const routeIdsByStopId = new Map<string, Set<string>>();
  const stopTimesByStopId = new Map<string, StopTimeInfo[]>();
  const serviceCalendarsById = new Map<string, ServiceCalendarInfo>();
  const serviceExceptionsByDate = new Map<number, Map<string, 1 | 2>>();

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
    if (!row.trip_id || !row.route_id || !row.service_id) {
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

    const departureSeconds = parseGtfsTimeToSeconds(getField(row, "departure_time"));
    if (departureSeconds === null) {
      continue;
    }

    const stopTimes = stopTimesByStopId.get(stopId) ?? [];
    stopTimes.push({
      tripId,
      stopId,
      departureSeconds,
      stopHeadsign: getField(row, "stop_headsign"),
    });
    stopTimesByStopId.set(stopId, stopTimes);
  }

  if (calendarText) {
    for (const row of parseCsvRecords(calendarText)) {
      const serviceId = getField(row, "service_id");
      if (!serviceId) {
        continue;
      }
      const calendar = toServiceCalendarInfo(row);
      if (!calendar) {
        continue;
      }
      serviceCalendarsById.set(serviceId, calendar);
    }
  }

  if (calendarDatesText) {
    for (const row of parseCsvRecords(calendarDatesText)) {
      const serviceId = getField(row, "service_id");
      const dateKey = parseGtfsDateKey(getField(row, "date"));
      const exceptionType = Number(getField(row, "exception_type"));
      if (!serviceId || !dateKey || (exceptionType !== 1 && exceptionType !== 2)) {
        continue;
      }
      const byServiceId = serviceExceptionsByDate.get(dateKey) ?? new Map<string, 1 | 2>();
      byServiceId.set(serviceId, exceptionType);
      serviceExceptionsByDate.set(dateKey, byServiceId);
    }
  }

  for (const stop of stopsById.values()) {
    const routeIds = getRouteIdsForStop(stop, routeIdsByStopId, childrenByParentId);
    stop.transportModes = toSortedTransportModes(routeIds, routesById);
    stop.lineHints = toSortedLineHints(routeIds, routesById);
  }

  return {
    fetchedAtUnix: Math.floor(Date.now() / 1000),
    stopsById,
    routesById,
    tripsById,
    childrenByParentId,
    stopTimesByStopId,
    serviceCalendarsById,
    serviceExceptionsByDate,
  };
};

export const loadRouenStaticData = async (staticGtfsUrl: string, ttlMinutes: number): Promise<StaticGtfsData> => {
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
        throw new Error(`Failed to download Rouen static GTFS: ${response.status}`);
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

type CitywayTripPoint = {
  Id: number;
  LogicalStopId: number;
  Latitude: number;
  Longitude: number;
  Name?: string;
};

type CitywayTripPointResponse = {
  Data?: CitywayTripPoint[];
  StatusCode?: number;
  Message?: string;
};

type CitywayLine = {
  id?: number;
  number?: string;
  name?: string;
};

type CitywayDirection = {
  id?: number;
  name?: string;
};

type CitywayStop = {
  id?: number;
  code?: string;
  name?: string;
};

type CitywayTimeDestination = {
  name?: string;
};

type CitywayTime = {
  dateTime?: string;
  realDateTime?: string | null;
  destination?: CitywayTimeDestination;
};

type CitywayLineEntry = {
  line?: CitywayLine;
  direction?: CitywayDirection;
  stop?: CitywayStop;
  times?: CitywayTime[];
};

type CitywayLogicalStopGroup = {
  lines?: CitywayLineEntry[];
};

const parseCitywayLocalDateTimeToUnix = (value: string): number | null => {
  const match = value.match(
    /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?$/,
  );
  if (!match) {
    return null;
  }
  const year = Number(match[1] ?? "0");
  const month = Number(match[2] ?? "0");
  const day = Number(match[3] ?? "0");
  const hour = Number(match[4] ?? "0");
  const minute = Number(match[5] ?? "0");
  const second = Number(match[6] ?? "0");
  if (
    !Number.isFinite(year) ||
    !Number.isFinite(month) ||
    !Number.isFinite(day) ||
    !Number.isFinite(hour) ||
    !Number.isFinite(minute) ||
    !Number.isFinite(second)
  ) {
    return null;
  }
  const dateKey = year * 10000 + month * 100 + day;
  const secondsFromMidnight = hour * 3600 + minute * 60 + second;
  return toUnixFromParisDateAndSeconds(dateKey, secondsFromMidnight);
};

const dedupeCitywayPoints = (points: CitywayTripPoint[]) => {
  const mapById = new Map<number, CitywayTripPoint>();
  for (const point of points) {
    if (!Number.isFinite(point.Id) || !Number.isFinite(point.LogicalStopId)) {
      continue;
    }
    if (!mapById.has(point.Id)) {
      mapById.set(point.Id, point);
    }
  }
  return Array.from(mapById.values());
};

const fetchCitywayTripPointsNear = async (lat: number, lon: number) => {
  const delta = 0.0015;
  const url = new URL(
    "https://api.mrn.cityway.fr/api/transport/v3/trippoint/GetTripPointsByBoundingBox",
  );
  url.searchParams.set("MinimumLatitude", String(lat - delta));
  url.searchParams.set("MinimumLongitude", String(lon - delta));
  url.searchParams.set("MaximumLatitude", String(lat + delta));
  url.searchParams.set("MaximumLongitude", String(lon + delta));
  url.searchParams.set("PointTypes", "5");
  url.searchParams.set("UserLat", String(lat));
  url.searchParams.set("UserLon", String(lon));

  const response = await fetch(url.toString(), {
    headers: {
      accept: "application/json",
    },
  });
  if (!response.ok) {
    throw new Error(`Cityway trip points lookup failed: ${response.status}`);
  }

  const payload = (await response.json()) as CitywayTripPointResponse;
  const points = Array.isArray(payload?.Data) ? payload.Data : [];
  return dedupeCitywayPoints(points);
};

const mapStopToCitywayPhysicalId = (
  stop: StopInfo,
  points: CitywayTripPoint[],
): number | null => {
  if (!points.length) {
    return null;
  }

  if (stop.lat === null || stop.lon === null) {
    const byName = points.find(
      (point) =>
        normalizeForSearch(point.Name ?? "") === normalizeForSearch(stop.name),
    );
    return byName?.Id ?? points[0]?.Id ?? null;
  }

  let bestPoint: CitywayTripPoint | null = null;
  let bestScore = Number.POSITIVE_INFINITY;

  for (const point of points) {
    const dLat = point.Latitude - stop.lat;
    const dLon = point.Longitude - stop.lon;
    const score = dLat * dLat + dLon * dLon;
    if (score < bestScore) {
      bestScore = score;
      bestPoint = point;
    }
  }

  return bestPoint?.Id ?? null;
};

export const getRouenDeparturesForStop = async (args: {
  stopId: string;
  maxMinutesAhead: number;
  limit: number;
  lines: string[];
  staticGtfsUrl: string;
  staticCacheTtlMinutes: number;
  tripUpdatesUrls: string[];
}) => {
  const nowUnix = Math.floor(Date.now() / 1000);
  const maxUnix = nowUnix + args.maxMinutesAhead * 60;
  const lineFilter = new Set(args.lines.map(normalizeLineName));
  const hasLineFilter = lineFilter.size > 0;

  const staticData = await loadRouenStaticData(
    args.staticGtfsUrl,
    args.staticCacheTtlMinutes,
  );
  const requestedStop = staticData.stopsById.get(args.stopId) ?? null;
  if (!requestedStop) {
    return {
      generatedAtUnix: nowUnix,
      feedTimestampUnix: nowUnix,
      stop: null,
      departures: [],
    };
  }

  const targetStops: StopInfo[] =
    requestedStop.locationType === 1
      ? (staticData.childrenByParentId.get(requestedStop.id) ?? [])
          .map((childId) => staticData.stopsById.get(childId))
          .filter((stop): stop is StopInfo => !!stop)
      : [requestedStop];
  if (!targetStops.length) {
    targetStops.push(requestedStop);
  }

  const pivotLat = requestedStop.lat ?? targetStops[0]?.lat;
  const pivotLon = requestedStop.lon ?? targetStops[0]?.lon;
  if (pivotLat === null || pivotLon === null || pivotLat === undefined || pivotLon === undefined) {
    throw new Error("Missing stop coordinates for Cityway schedule lookup");
  }

  const citywayPoints = await fetchCitywayTripPointsNear(pivotLat, pivotLon);
  if (!citywayPoints.length) {
    return {
      generatedAtUnix: nowUnix,
      feedTimestampUnix: nowUnix,
      stop: requestedStop,
      departures: [],
    };
  }

  const stopIdByPhysicalId = new Map<number, string>();
  const allowedPhysicalStopIds = new Set<number>();

  for (const stop of targetStops) {
    const physicalId = mapStopToCitywayPhysicalId(stop, citywayPoints);
    if (physicalId !== null) {
      allowedPhysicalStopIds.add(physicalId);
      stopIdByPhysicalId.set(physicalId, stop.id);
    }
  }

  const requestedPhysicalId = mapStopToCitywayPhysicalId(
    requestedStop,
    citywayPoints,
  );
  const fallbackPoint = requestedPhysicalId
    ? citywayPoints.find((point) => point.Id === requestedPhysicalId)
    : citywayPoints[0];
  const logicalStopId = fallbackPoint?.LogicalStopId;
  if (!logicalStopId) {
    throw new Error("Unable to resolve Cityway logical stop id");
  }

  const logicalUrl = new URL(
    `https://api.mrn.cityway.fr/media/api/v1/en/Schedules/LogicalStop/${logicalStopId}/NextDeparture`,
  );
  logicalUrl.searchParams.set("realTime", "true");
  logicalUrl.searchParams.set("lineId", "");
  logicalUrl.searchParams.set("direction", "");
  logicalUrl.searchParams.set("userLat", String(pivotLat));
  logicalUrl.searchParams.set("userLon", String(pivotLon));
  logicalUrl.searchParams.set("userId", "TSI_MRN");

  const response = await fetch(logicalUrl.toString(), {
    headers: {
      accept: "application/json",
    },
  });
  if (!response.ok) {
    throw new Error(
      `Cityway logical stop departures failed: ${response.status}`,
    );
  }

  const groups = (await response.json()) as CitywayLogicalStopGroup[];
  const departures: Departure[] = [];
  const seen = new Set<string>();

  for (const group of Array.isArray(groups) ? groups : []) {
    for (const lineEntry of group.lines ?? []) {
      const lineNumber = (lineEntry.line?.number ?? "").trim();
      if (!lineNumber) {
        continue;
      }
      if (hasLineFilter && !lineFilter.has(normalizeLineName(lineNumber))) {
        continue;
      }

      const physicalStopId =
        typeof lineEntry.stop?.id === "number" ? lineEntry.stop.id : null;
      if (
        allowedPhysicalStopIds.size > 0 &&
        (physicalStopId === null || !allowedPhysicalStopIds.has(physicalStopId))
      ) {
        continue;
      }

      for (const time of lineEntry.times ?? []) {
        const effectiveDateTime =
          (time.realDateTime && time.realDateTime.trim()) ||
          (time.dateTime && time.dateTime.trim()) ||
          "";
        const departureUnix = parseCitywayLocalDateTimeToUnix(effectiveDateTime);
        if (
          departureUnix === null ||
          departureUnix < nowUnix ||
          departureUnix > maxUnix
        ) {
          continue;
        }

        const destination =
          time.destination?.name?.trim() ||
          lineEntry.direction?.name?.trim() ||
          lineEntry.line?.name?.trim() ||
          DEFAULT_STRING;
        const dedupeKey = `${lineNumber}|${physicalStopId ?? "?"}|${destination}|${departureUnix}`;
        if (seen.has(dedupeKey)) {
          continue;
        }
        seen.add(dedupeKey);

        const mappedStopId =
          (physicalStopId !== null ? stopIdByPhysicalId.get(physicalStopId) : null) ??
          requestedStop.id;
        const mappedStopInfo = staticData.stopsById.get(mappedStopId) ?? requestedStop;

        departures.push({
          stopId: mappedStopId,
          stopName: mappedStopInfo.name,
          routeId: String(lineEntry.line?.id ?? lineNumber),
          line: lineNumber,
          destination,
          departureUnix,
          departureIso: new Date(departureUnix * 1000).toISOString(),
          minutesUntilDeparture: Math.max(
            0,
            Math.round((departureUnix - nowUnix) / 60),
          ),
          sourceUrl: logicalUrl.toString(),
          isRealtime: Boolean(
            time.realDateTime && time.realDateTime.trim().length > 0,
          ),
        });
      }
    }
  }

  departures.sort((a, b) => a.departureUnix - b.departureUnix);

  return {
    generatedAtUnix: nowUnix,
    feedTimestampUnix: nowUnix,
    stop: requestedStop,
    departures: departures.slice(0, args.limit),
  };
};

export const searchRouenStops = async (args: { query: string; limit: number; staticGtfsUrl: string; staticCacheTtlMinutes: number }) => {
  const staticData = await loadRouenStaticData(args.staticGtfsUrl, args.staticCacheTtlMinutes);

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

    const isMatch = normalizedName.includes(q) || normalizedId.includes(q) || normalizedCode.includes(q);
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
