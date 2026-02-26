import { parse } from "csv-parse/sync";
import JSZip from "jszip";

import {
  buildRouteColorByNormalizedLine,
  getField,
  getProviderPriority,
  getRouteIdsForStop,
  normalizeForSearch,
  normalizeHexColor,
  parseGtfsDateKey,
  toLineHintColors,
  toServiceCalendarInfo,
  toSortedLineHints,
  toSortedTransportModes,
} from "./helpers";
import type {
  Cache,
  RouteInfo,
  SearchableStopEntry,
  ServiceCalendarInfo,
  StaticGtfsData,
  StopInfo,
  TripInfo,
} from "./types";

const staticCache: Cache = {
  data: null,
  expiresAtUnix: 0,
  loadingPromise: null,
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

const toRouteInfo = (row: Record<string, string>): RouteInfo => ({
  id: getField(row, "route_id"),
  shortName: getField(row, "route_short_name") || getField(row, "route_id"),
  longName:
    getField(row, "route_long_name") ||
    getField(row, "route_short_name") ||
    getField(row, "route_id"),
  type: getField(row, "route_type") ? Number(getField(row, "route_type")) : null,
  color: normalizeHexColor(getField(row, "route_color")),
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
  lineHintColors: {},
});

const toTripInfo = (row: Record<string, string>): TripInfo => ({
  id: getField(row, "trip_id"),
  routeId: getField(row, "route_id"),
  headsign: getField(row, "trip_headsign"),
  serviceId: getField(row, "service_id"),
});

const parseGtfsZip = async (zipBuffer: ArrayBuffer): Promise<StaticGtfsData> => {
  const zip = await JSZip.loadAsync(zipBuffer);
  const [
    stopsText,
    routesText,
    tripsText,
    stopTimesText,
    calendarText,
    calendarDatesText,
  ] = await Promise.all([
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
      const byServiceId =
        serviceExceptionsByDate.get(dateKey) ?? new Map<string, 1 | 2>();
      byServiceId.set(serviceId, exceptionType);
      serviceExceptionsByDate.set(dateKey, byServiceId);
    }
  }

  for (const stop of stopsById.values()) {
    const routeIds = getRouteIdsForStop(stop, routeIdsByStopId, childrenByParentId);
    stop.transportModes = toSortedTransportModes(routeIds, routesById);
    stop.lineHints = toSortedLineHints(routeIds, routesById);
    stop.lineHintColors = toLineHintColors(routeIds, routesById);
  }

  const routeColorByNormalizedLine = buildRouteColorByNormalizedLine(routesById);
  const searchableStops: SearchableStopEntry[] = Array.from(stopsById.values()).map(
    (stop) => ({
      stop,
      normalizedName: normalizeForSearch(stop.name),
      normalizedId: normalizeForSearch(stop.id),
      normalizedCode: normalizeForSearch(stop.stopCode ?? ""),
      hasKnownService: stop.transportModes.length > 0,
      isBoardingStop: stop.locationType !== 1,
      providerPriority: getProviderPriority(stop.id),
      lineHintCount: stop.lineHints.length,
    }),
  );

  return {
    fetchedAtUnix: Math.floor(Date.now() / 1000),
    stopsById,
    routesById,
    tripsById,
    childrenByParentId,
    serviceCalendarsById,
    serviceExceptionsByDate,
    routeColorByNormalizedLine,
    searchableStops,
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
