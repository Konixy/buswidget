import { normalizeForSearch, toUnixFromParisDateAndSeconds } from "./helpers";
import type { StopInfo, TimedCacheEntry } from "./types";

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

const CITYWAY_TRIP_POINTS_CACHE_TTL_SECONDS = 300;
const CITYWAY_CACHE_MAX_ENTRIES = 256;

const citywayTripPointsCache = new Map<
  string,
  TimedCacheEntry<CitywayTripPoint[]>
>();

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
