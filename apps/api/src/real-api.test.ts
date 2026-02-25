import { describe, expect, it } from "bun:test";
import * as v from "valibot";

import {
  stopDeparturesResponseSchema,
  stopSearchResponseSchema,
} from "./contracts";
import { env } from "./env";
import { createApp } from "./index";
import { getRouenDeparturesForStop, searchRouenStops } from "./lib/rouen-astuce";

const app = createApp({
  config: env,
  searchStops: searchRouenStops,
  getDepartures: getRouenDeparturesForStop,
});

describe("real Rouen APIs", () => {
  it("validates stop search payload shape from live feed", async () => {
    const response = await app.request("/v1/rouen/stops/search?q=gare&limit=5");
    expect(response.status).toBe(200);

    const json = await response.json();
    const parsed = v.safeParse(stopSearchResponseSchema, json);

    expect(parsed.success).toBe(true);
    if (!parsed.success) {
      throw new Error(JSON.stringify(parsed.issues));
    }

    expect(parsed.output.count).toBeGreaterThan(0);
    expect(parsed.output.results.length).toBeGreaterThan(0);
    expect(
      parsed.output.results.some((stop) => stop.transportModes.length > 0),
    ).toBe(true);
  }, 30000);

  it("validates departures payload shape from live feed", async () => {
    const searchResponse = await app.request("/v1/rouen/stops/search?q=gare&limit=1");
    expect(searchResponse.status).toBe(200);

    const searchJson = await searchResponse.json();
    const searchParsed = v.parse(stopSearchResponseSchema, searchJson);
    expect(searchParsed.results.length).toBeGreaterThan(0);

    const stopId = searchParsed.results[0]?.id;
    if (!stopId) {
      throw new Error("No stop id returned from live search");
    }

    const departuresResponse = await app.request(
      `/v1/rouen/stops/${encodeURIComponent(stopId)}/departures?limit=5&maxMinutes=240`,
    );
    expect(departuresResponse.status).toBe(200);

    const departuresJson = await departuresResponse.json();
    const departuresParsed = v.safeParse(
      stopDeparturesResponseSchema,
      departuresJson,
    );

    expect(departuresParsed.success).toBe(true);
    if (!departuresParsed.success) {
      throw new Error(JSON.stringify(departuresParsed.issues));
    }

    expect(departuresParsed.output.stop?.id).toBe(stopId);
    expect(departuresParsed.output.feedTimestampUnix).toBeGreaterThan(0);
  }, 30000);
});
