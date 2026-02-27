import {
  fetchCitywayLogicalDepartures,
  fetchCitywayTripPointsNear,
  mapStopToCitywayPhysicalId,
  toCitywayDepartureUnix,
} from "./cityway";
import { DEFAULT_STRING, normalizeHexColor, normalizeLineName } from "./helpers";
import { loadRouenStaticData } from "./static-data";
import type { Departure, StopDeparturesResponse, StopInfo } from "./types";

export const getRouenDeparturesForStop = async (args: {
  stopId: string;
  maxMinutesAhead: number;
  limit: number;
  lines: string[];
  staticGtfsUrl: string;
  staticCacheTtlMinutes: number;
  tripUpdatesUrls: string[];
}): Promise<StopDeparturesResponse> => {
  const nowUnix = Math.floor(Date.now() / 1000);
  const maxUnix = nowUnix + args.maxMinutesAhead * 60;
  const lineFilter = new Set(args.lines.map(normalizeLineName));
  const hasLineFilter = lineFilter.size > 0;

  const staticData = await loadRouenStaticData(
    args.staticGtfsUrl,
    args.staticCacheTtlMinutes,
  );
  const routeColorByNormalizedLine = staticData.routeColorByNormalizedLine;
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
  if (
    pivotLat === null ||
    pivotLon === null ||
    pivotLat === undefined ||
    pivotLon === undefined
  ) {
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
  let requestedPhysicalId: number | null = null;

  for (const stop of targetStops) {
    const physicalId = mapStopToCitywayPhysicalId(stop, citywayPoints);
    if (physicalId !== null) {
      allowedPhysicalStopIds.add(physicalId);
      stopIdByPhysicalId.set(physicalId, stop.id);
      if (stop.id === requestedStop.id) {
        requestedPhysicalId = physicalId;
      }
    }
  }

  if (requestedPhysicalId === null) {
    requestedPhysicalId = mapStopToCitywayPhysicalId(requestedStop, citywayPoints);
  }
  const fallbackPoint = requestedPhysicalId
    ? citywayPoints.find((point) => point.Id === requestedPhysicalId)
    : citywayPoints[0];
  const logicalStopId = fallbackPoint?.LogicalStopId;
  if (!logicalStopId) {
    throw new Error("Unable to resolve Cityway logical stop id");
  }

  const logicalDeparturesPayload = await fetchCitywayLogicalDepartures(
    logicalStopId,
    pivotLat,
    pivotLon,
  );
  const groups = logicalDeparturesPayload.groups;
  const departures: Departure[] = [];
  const seen = new Set<string>();

  for (const group of Array.isArray(groups) ? groups : []) {
    for (const lineEntry of group.lines ?? []) {
      const lineNumber = (lineEntry.line?.number ?? "").trim();
      if (!lineNumber) {
        continue;
      }
      const normalizedLine = normalizeLineName(lineNumber);
      if (hasLineFilter && !lineFilter.has(normalizedLine)) {
        continue;
      }
      const fallbackLineColor = routeColorByNormalizedLine.get(normalizedLine) ?? null;
      const routeId = String(lineEntry.line?.id ?? lineNumber);
      const citywayLineColor = normalizeHexColor(lineEntry.line?.color ?? "");

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
        const departureUnix = toCitywayDepartureUnix(effectiveDateTime);
        if (departureUnix === null || departureUnix < nowUnix || departureUnix > maxUnix) {
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
        const hasRealtime = Boolean(
          time.realDateTime && time.realDateTime.trim().length > 0,
        );

        departures.push({
          stopId: mappedStopId,
          stopName: mappedStopInfo.name,
          routeId,
          line: lineNumber,
          lineColor: citywayLineColor ?? fallbackLineColor,
          destination,
          departureUnix,
          departureIso: new Date(departureUnix * 1000).toISOString(),
          minutesUntilDeparture: Math.max(
            0,
            Math.round((departureUnix - nowUnix) / 60),
          ),
          sourceUrl: logicalDeparturesPayload.sourceUrl,
          isRealtime: hasRealtime,
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
