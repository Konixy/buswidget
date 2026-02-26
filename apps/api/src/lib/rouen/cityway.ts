import {
  DEFAULT_STRING,
  fromLongLike,
  normalizeForSearch,
  normalizeHexColor,
  normalizeLineName,
  toParisDateKey,
  toUnixFromParisDateAndSeconds,
} from "./helpers";
import type { Departure, StopInfo, TimedCacheEntry } from "./types";

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
  color?: string;
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

export type CitywayLogicalDeparturesPayload = {
  groups: CitywayLogicalStopGroup[];
  sourceUrl: string;
};

type CitywayServiceEnvelope<T> = {
  Data?: T | null;
  StatusCode?: number;
  Message?: string | null;
};

type CitywayTimetableStopHour = {
  LineId?: number;
  StopId?: number;
  VehicleJourneyId?: number;
  TheoricDepartureTime?: number;
  AimedDepartureTime?: number;
  PredictedDepartureTime?: number;
  RealDepartureTime?: number;
  RealTimeStatus?: number;
  IsCancelled?: boolean;
};

type CitywayTimetableLine = {
  Id?: number;
  Number?: string;
  Name?: string;
  Color?: string;
};

type CitywayTimetableStop = {
  Id?: number;
  LogicalId?: number;
  Name?: string;
  Code?: string;
  Latitude?: number;
  Longitude?: number;
};

type CitywayTimetableVehicleJourney = {
  Id?: number;
  JourneyDestination?: string;
};

type CitywayTimetableData = {
  Hours?: CitywayTimetableStopHour[];
  Lines?: CitywayTimetableLine[];
  Stops?: CitywayTimetableStop[];
  VehicleJourneys?: CitywayTimetableVehicleJourney[];
  ServerTime?: string;
};

export type CitywayTimetablePayload = {
  data: CitywayTimetableData | null;
  sourceUrl: string;
};

const CITYWAY_TRIP_POINTS_CACHE_TTL_SECONDS = 300;
const CITYWAY_CACHE_MAX_ENTRIES = 256;

const citywayTripPointsCache = new Map<
  string,
  TimedCacheEntry<CitywayTripPoint[]>
>();

const parseCitywayLocalDateTimeToUnix = (value: string): number | null => {
  const match = value.match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?$/);
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

export const parseCitywayServerTimeToUnix = (
  value: string | undefined,
): number | null => {
  if (!value) {
    return null;
  }
  const match = value.match(/^\/Date\((\d+)(?:[+-]\d{4})?\)\/$/);
  if (!match) {
    return null;
  }
  const milliseconds = Number(match[1] ?? "0");
  if (!Number.isFinite(milliseconds)) {
    return null;
  }
  return Math.floor(milliseconds / 1000);
};

const parseCitywayClockToSeconds = (value: unknown): number | null => {
  const numeric = fromLongLike(value);
  if (numeric === null) {
    return null;
  }
  const hhmm = Math.trunc(numeric);
  if (hhmm < 0) {
    return null;
  }
  const hours = Math.floor(hhmm / 100);
  const minutes = hhmm % 100;
  if (hours < 0 || minutes < 0) {
    return null;
  }
  return hours * 3600 + minutes * 60;
};

const getTimetableDepartureUnix = (
  hour: CitywayTimetableStopHour,
  nowUnix: number,
): number | null => {
  const departureSeconds =
    parseCitywayClockToSeconds(hour.RealDepartureTime) ??
    parseCitywayClockToSeconds(hour.PredictedDepartureTime) ??
    parseCitywayClockToSeconds(hour.AimedDepartureTime) ??
    parseCitywayClockToSeconds(hour.TheoricDepartureTime);
  if (departureSeconds === null) {
    return null;
  }

  const currentDateKey = toParisDateKey(nowUnix);
  let departureUnix = toUnixFromParisDateAndSeconds(currentDateKey, departureSeconds);
  if (departureUnix < nowUnix - 1800) {
    departureUnix += 86400;
  }
  return departureUnix;
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

const getNowUnix = () => Math.floor(Date.now() / 1000);

const pruneTimedCache = <K, T>(cache: Map<K, TimedCacheEntry<T>>) => {
  if (cache.size <= CITYWAY_CACHE_MAX_ENTRIES) {
    return;
  }

  const nowUnix = getNowUnix();
  for (const [key, entry] of cache.entries()) {
    if (!entry.loadingPromise && entry.expiresAtUnix <= nowUnix) {
      cache.delete(key);
    }
    if (cache.size <= CITYWAY_CACHE_MAX_ENTRIES) {
      return;
    }
  }

  while (cache.size > CITYWAY_CACHE_MAX_ENTRIES) {
    const oldestKey = cache.keys().next().value as K | undefined;
    if (oldestKey === undefined) {
      return;
    }
    cache.delete(oldestKey);
  }
};

const toCoordinatesCacheKey = (lat: number, lon: number) =>
  `${lat.toFixed(5)}:${lon.toFixed(5)}`;

export const fetchCitywayTripPointsNear = async (lat: number, lon: number) => {
  const cacheKey = toCoordinatesCacheKey(lat, lon);
  const nowUnix = getNowUnix();
  const existing = citywayTripPointsCache.get(cacheKey);
  if (existing?.value && nowUnix < existing.expiresAtUnix) {
    return existing.value;
  }
  if (existing?.loadingPromise) {
    return existing.loadingPromise;
  }

  const cacheEntry: TimedCacheEntry<CitywayTripPoint[]> = existing ?? {
    value: null,
    expiresAtUnix: 0,
    loadingPromise: null,
  };

  cacheEntry.loadingPromise = (async () => {
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
    const points = dedupeCitywayPoints(Array.isArray(payload?.Data) ? payload.Data : []);
    cacheEntry.value = points;
    cacheEntry.expiresAtUnix = getNowUnix() + CITYWAY_TRIP_POINTS_CACHE_TTL_SECONDS;
    pruneTimedCache(citywayTripPointsCache);
    return points;
  })();
  citywayTripPointsCache.set(cacheKey, cacheEntry);

  try {
    return await cacheEntry.loadingPromise;
  } catch (error) {
    if (cacheEntry.value) {
      return cacheEntry.value;
    }
    throw error;
  } finally {
    cacheEntry.loadingPromise = null;
  }
};

export const fetchCitywayLogicalDepartures = async (
  logicalStopId: number,
  userLat: number,
  userLon: number,
): Promise<CitywayLogicalDeparturesPayload> => {
  const logicalUrl = new URL(
    `https://api.mrn.cityway.fr/media/api/v1/en/Schedules/LogicalStop/${logicalStopId}/NextDeparture`,
  );
  logicalUrl.searchParams.set("realTime", "true");
  logicalUrl.searchParams.set("lineId", "");
  logicalUrl.searchParams.set("direction", "");
  logicalUrl.searchParams.set("userLat", String(userLat));
  logicalUrl.searchParams.set("userLon", String(userLon));
  logicalUrl.searchParams.set("userId", "TSI_MRN");

  const response = await fetch(logicalUrl.toString(), {
    headers: {
      accept: "application/json",
    },
  });
  if (!response.ok) {
    throw new Error(`Cityway logical stop departures failed: ${response.status}`);
  }

  return {
    groups: ((await response.json()) as CitywayLogicalStopGroup[]) ?? [],
    sourceUrl: logicalUrl.toString(),
  };
};

export const fetchCitywayTimetableByLogicalStop = async (
  logicalStopId: number,
  maxItems: number,
): Promise<CitywayTimetablePayload> => {
  const timetableUrl = new URL(
    "https://tsvc.mrn.cityway.fr/api/transport/v3/timetable/GetNextStopHours/json",
  );
  const boundedItems = Math.max(5, Math.min(120, maxItems));
  timetableUrl.searchParams.set("LogicalStopIds", String(logicalStopId));
  timetableUrl.searchParams.set("MaxItemsByStop", String(boundedItems));
  timetableUrl.searchParams.set(
    "MaxTotalItems",
    String(Math.max(20, Math.min(200, boundedItems * 3))),
  );
  timetableUrl.searchParams.set("MaxLines", "20");
  timetableUrl.searchParams.set(
    "MaxItemsByLine",
    String(Math.max(6, Math.min(20, boundedItems))),
  );
  timetableUrl.searchParams.set("TimeTableType", "2");
  timetableUrl.searchParams.set("Lang", "en");
  timetableUrl.searchParams.set("UserRequestRef", "buswidget-api");

  const response = await fetch(timetableUrl.toString(), {
    headers: {
      accept: "application/json",
    },
  });
  if (!response.ok) {
    throw new Error(`Cityway timetable lookup failed: ${response.status}`);
  }

  const payload = (await response.json()) as CitywayServiceEnvelope<CitywayTimetableData>;
  const statusCode = payload.StatusCode ?? 200;
  if (statusCode >= 400) {
    throw new Error(
      `Cityway timetable logical stop failed: ${statusCode}${payload.Message ? ` ${payload.Message}` : ""}`,
    );
  }

  return {
    data: payload.Data ?? null,
    sourceUrl: timetableUrl.toString(),
  };
};

export const toLogicalStopInfo = (
  logicalStopId: number,
  data: CitywayTimetableData | null,
): StopInfo | null => {
  if (!data?.Stops?.length) {
    return null;
  }
  const candidate =
    data.Stops.find((stop) => fromLongLike(stop.LogicalId) === logicalStopId) ??
    data.Stops[0];
  if (!candidate) {
    return null;
  }

  return {
    id: `CITYWAY:logical:${logicalStopId}`,
    name: candidate.Name?.trim() || `Logical stop ${logicalStopId}`,
    lat: fromLongLike(candidate.Latitude),
    lon: fromLongLike(candidate.Longitude),
    stopCode: candidate.Code?.trim() || null,
    locationType: 1,
    parentStationId: null,
    transportModes: [],
    lineHints: [],
    lineHintColors: {},
  };
};

export const toDeparturesFromTimetable = (args: {
  data: CitywayTimetableData | null;
  sourceUrl: string;
  logicalStopId: number;
  lineFilter: Set<string>;
  hasLineFilter: boolean;
  nowUnix: number;
  maxUnix: number;
}): Departure[] => {
  const linesById = new Map<number, CitywayTimetableLine>();
  for (const line of args.data?.Lines ?? []) {
    const lineId = fromLongLike(line.Id);
    if (lineId !== null) {
      linesById.set(lineId, line);
    }
  }

  const stopsById = new Map<number, CitywayTimetableStop>();
  for (const stop of args.data?.Stops ?? []) {
    const stopId = fromLongLike(stop.Id);
    if (stopId !== null) {
      stopsById.set(stopId, stop);
    }
  }

  const journeysById = new Map<number, CitywayTimetableVehicleJourney>();
  for (const journey of args.data?.VehicleJourneys ?? []) {
    const journeyId = fromLongLike(journey.Id);
    if (journeyId !== null) {
      journeysById.set(journeyId, journey);
    }
  }

  const departures: Departure[] = [];
  const seen = new Set<string>();
  for (const hour of args.data?.Hours ?? []) {
    if (hour.IsCancelled) {
      continue;
    }

    const departureUnix = getTimetableDepartureUnix(hour, args.nowUnix);
    if (departureUnix === null || departureUnix < args.nowUnix || departureUnix > args.maxUnix) {
      continue;
    }

    const lineId = fromLongLike(hour.LineId);
    const lineInfo = lineId !== null ? linesById.get(lineId) : null;
    const lineNumber = (
      lineInfo?.Number ??
      lineInfo?.Name ??
      (lineId !== null ? String(lineId) : "")
    ).trim();
    if (!lineNumber) {
      continue;
    }

    const normalizedLine = normalizeLineName(lineNumber);
    if (args.hasLineFilter && !args.lineFilter.has(normalizedLine)) {
      continue;
    }

    const stopId = fromLongLike(hour.StopId);
    const stopInfo = stopId !== null ? stopsById.get(stopId) : null;
    const vehicleJourneyId = fromLongLike(hour.VehicleJourneyId);
    const journeyInfo =
      vehicleJourneyId !== null ? journeysById.get(vehicleJourneyId) : null;
    const destination =
      journeyInfo?.JourneyDestination?.trim() ||
      stopInfo?.Name?.trim() ||
      lineInfo?.Name?.trim() ||
      DEFAULT_STRING;

    const dedupeKey = `${lineNumber}|${stopId ?? "?"}|${destination}|${departureUnix}`;
    if (seen.has(dedupeKey)) {
      continue;
    }
    seen.add(dedupeKey);

    const realtimeStatus = fromLongLike(hour.RealTimeStatus) ?? 0;
    const realtimeDeparture = fromLongLike(hour.RealDepartureTime);
    const predictedDeparture = fromLongLike(hour.PredictedDepartureTime);
    const theoricDeparture = fromLongLike(hour.TheoricDepartureTime);
    const hasRealtime =
      realtimeStatus !== 0 ||
      realtimeDeparture !== null ||
      (predictedDeparture !== null && predictedDeparture !== theoricDeparture);
    const lineColor = normalizeHexColor(lineInfo?.Color ?? "");

    departures.push({
      stopId:
        stopId !== null ? `CITYWAY:${stopId}` : `CITYWAY:logical:${args.logicalStopId}`,
      stopName: stopInfo?.Name?.trim() || DEFAULT_STRING,
      routeId: lineId !== null ? String(lineId) : lineNumber,
      line: lineNumber,
      lineColor,
      destination,
      departureUnix,
      departureIso: new Date(departureUnix * 1000).toISOString(),
      minutesUntilDeparture: Math.max(0, Math.round((departureUnix - args.nowUnix) / 60)),
      sourceUrl: args.sourceUrl,
      isRealtime: hasRealtime,
    });
  }

  departures.sort((a, b) => a.departureUnix - b.departureUnix);
  return departures;
};

export const mapStopToCitywayPhysicalId = (
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

export const toCitywayDepartureUnix = (value: string): number | null => {
  return parseCitywayLocalDateTimeToUnix(value);
};
