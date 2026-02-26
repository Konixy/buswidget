import type { RouteInfo, ServiceCalendarInfo, StopInfo } from "./types";

export const DEFAULT_STRING = "Unknown";
const TEOR_LINE_RE = /^T\d+$/i;
const ROUTE_MODE_PRIORITY = ["Metro", "Tram", "TEOR", "Bus", "Train", "Ferry"];
const PROVIDER_PRIORITY: Record<string, number> = {
  TCAR: 0,
  TNI: 1,
  TAE: 2,
};

const PARIS_TIME_ZONE = "Europe/Paris";

const PARIS_DATE_FORMATTER = new Intl.DateTimeFormat("en-CA", {
  timeZone: PARIS_TIME_ZONE,
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
});

const PARIS_OFFSET_FORMATTER = new Intl.DateTimeFormat("en-US", {
  timeZone: PARIS_TIME_ZONE,
  timeZoneName: "shortOffset",
  hour: "2-digit",
  minute: "2-digit",
  hour12: false,
});

export const getField = (row: Record<string, string>, key: string): string => {
  return row[key] ?? "";
};

export const normalizeHexColor = (value: string): string | null => {
  const normalized = value.trim().replace(/^#/, "");
  if (!normalized) {
    return null;
  }
  if (!/^[0-9a-fA-F]{6}$/.test(normalized)) {
    return null;
  }
  return `#${normalized.toUpperCase()}`;
};

export const fromLongLike = (value: unknown): number | null => {
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

export const parseGtfsDateKey = (value: string): number | null => {
  if (!/^\d{8}$/.test(value)) {
    return null;
  }
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric : null;
};

const toWeekdayFlag = (value: string) => value === "1";

export const toServiceCalendarInfo = (
  row: Record<string, string>,
): ServiceCalendarInfo | null => {
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

export const toParisDateKey = (unixSeconds: number): number => {
  const date = new Date(unixSeconds * 1000);
  const parts = PARIS_DATE_FORMATTER.formatToParts(date);
  const year = Number(parts.find((part) => part.type === "year")?.value ?? "0");
  const month = Number(parts.find((part) => part.type === "month")?.value ?? "0");
  const day = Number(parts.find((part) => part.type === "day")?.value ?? "0");
  return year * 10000 + month * 100 + day;
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

export const toUnixFromParisDateAndSeconds = (
  dateKey: number,
  secondsFromMidnight: number,
): number => {
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

export const normalizeForSearch = (value: string) =>
  value.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase();

export const normalizeLineName = (value: string) =>
  value.trim().toLocaleUpperCase("fr-FR");

const getStopProvider = (stopId: string): string => {
  const [provider] = stopId.split(":", 1);
  return provider ?? "";
};

export const getProviderPriority = (stopId: string): number => {
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

export const getRouteIdsForStop = (
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

export const toSortedTransportModes = (
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

export const toSortedLineHints = (
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

export const toLineHintColors = (
  routeIds: Set<string>,
  routesById: Map<string, RouteInfo>,
) => {
  const routes = Array.from(routeIds)
    .map((routeId) => routesById.get(routeId))
    .filter((route): route is RouteInfo => Boolean(route))
    .sort((a, b) => {
      const lineCmp = a.shortName.localeCompare(b.shortName, "fr", {
        sensitivity: "base",
        numeric: true,
      });
      if (lineCmp !== 0) {
        return lineCmp;
      }
      return a.id.localeCompare(b.id, "fr", {
        sensitivity: "base",
        numeric: true,
      });
    });

  const colorsByLine = new Map<string, string>();
  for (const route of routes) {
    const line = route.shortName.trim();
    if (!line || !route.color || colorsByLine.has(line)) {
      continue;
    }
    colorsByLine.set(line, route.color);
  }

  return Object.fromEntries(colorsByLine.entries()) as Record<string, string>;
};

export const buildRouteColorByNormalizedLine = (
  routesById: Map<string, RouteInfo>,
) => {
  const routes = Array.from(routesById.values()).sort((a, b) => {
    const lineCmp = a.shortName.localeCompare(b.shortName, "fr", {
      sensitivity: "base",
      numeric: true,
    });
    if (lineCmp !== 0) {
      return lineCmp;
    }
    return a.id.localeCompare(b.id, "fr", {
      sensitivity: "base",
      numeric: true,
    });
  });

  const colorsByLine = new Map<string, string>();
  for (const route of routes) {
    const line = route.shortName.trim();
    if (!line || !route.color) {
      continue;
    }
    const normalizedLine = normalizeLineName(line);
    if (!colorsByLine.has(normalizedLine)) {
      colorsByLine.set(normalizedLine, route.color);
    }
  }

  return colorsByLine;
};
