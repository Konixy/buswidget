import * as v from "valibot";

export const stopSchema = v.object({
  id: v.string(),
  name: v.string(),
  lat: v.nullable(v.number()),
  lon: v.nullable(v.number()),
  stopCode: v.nullable(v.string()),
  locationType: v.nullable(v.number()),
  parentStationId: v.nullable(v.string()),
  transportModes: v.array(v.string()),
  lineHints: v.array(v.string()),
  lineHintColors: v.record(v.string(), v.string()),
});

export const departureSchema = v.object({
  stopId: v.string(),
  stopName: v.string(),
  routeId: v.string(),
  line: v.string(),
  lineColor: v.nullable(v.string()),
  destination: v.string(),
  departureUnix: v.number(),
  departureIso: v.string(),
  minutesUntilDeparture: v.number(),
  sourceUrl: v.string(),
  isRealtime: v.boolean(),
});

export const stopSearchResponseSchema = v.object({
  count: v.number(),
  results: v.array(stopSchema),
});

export const stopDeparturesResponseSchema = v.object({
  generatedAtUnix: v.number(),
  feedTimestampUnix: v.number(),
  stop: v.nullable(stopSchema),
  departures: v.array(departureSchema),
});
