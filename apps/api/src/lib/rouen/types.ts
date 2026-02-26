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
  lineHintColors: Record<string, string>;
};

export type RouteInfo = {
  id: string;
  shortName: string;
  longName: string;
  type: number | null;
  color: string | null;
};

export type TripInfo = {
  id: string;
  routeId: string;
  headsign: string;
  serviceId: string;
};

export type ServiceCalendarInfo = {
  startDate: number;
  endDate: number;
  activeWeekdays: [boolean, boolean, boolean, boolean, boolean, boolean, boolean];
};

export type SearchableStopEntry = {
  stop: StopInfo;
  normalizedName: string;
  normalizedId: string;
  normalizedCode: string;
  hasKnownService: boolean;
  isBoardingStop: boolean;
  providerPriority: number;
  lineHintCount: number;
};

export type StaticGtfsData = {
  fetchedAtUnix: number;
  stopsById: Map<string, StopInfo>;
  routesById: Map<string, RouteInfo>;
  tripsById: Map<string, TripInfo>;
  childrenByParentId: Map<string, string[]>;
  serviceCalendarsById: Map<string, ServiceCalendarInfo>;
  serviceExceptionsByDate: Map<number, Map<string, 1 | 2>>;
  routeColorByNormalizedLine: Map<string, string>;
  searchableStops: SearchableStopEntry[];
};

export type Departure = {
  stopId: string;
  stopName: string;
  routeId: string;
  line: string;
  lineColor: string | null;
  destination: string;
  departureUnix: number;
  departureIso: string;
  minutesUntilDeparture: number;
  sourceUrl: string;
  isRealtime: boolean;
};

export type StopDeparturesResponse = {
  generatedAtUnix: number;
  feedTimestampUnix: number;
  stop: StopInfo | null;
  logicalStopId: number | null;
  departures: Departure[];
};

export type Cache = {
  data: StaticGtfsData | null;
  expiresAtUnix: number;
  loadingPromise: Promise<StaticGtfsData> | null;
};

export type TimedCacheEntry<T> = {
  value: T | null;
  expiresAtUnix: number;
  loadingPromise: Promise<T> | null;
};
