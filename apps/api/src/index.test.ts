import { describe, expect, it, mock } from "bun:test";

import { createApp } from "./index";

const buildTestApp = () => {
  const searchStops = mock(async (_args: unknown) => ({
    count: 1,
    results: [{ id: "S1", name: "Stop 1", lat: null, lon: null }],
  }));
  const getDepartures = mock(async (_args: unknown) => ({
    generatedAtUnix: 0,
    feedTimestampUnix: 0,
    stop: null,
    logicalStopId: 123,
    departures: [{ stopId: "S1" }],
  }));
  const getDeparturesByLogicalStopId = mock(async (_args: unknown) => ({
    generatedAtUnix: 0,
    feedTimestampUnix: 0,
    stop: null,
    logicalStopId: 123,
    departures: [{ stopId: "S1" }],
  }));

  const app = createApp({
    config: {
      host: "127.0.0.1",
      port: 3000,
      rouenStaticGtfsUrl: "https://example.com/gtfs.zip",
      rouenTripUpdatesUrls: ["https://example.com/tripupdates.pb"],
      rouenStaticCacheTtlMinutes: 60,
    },
    searchStops,
    getDepartures,
    getDeparturesByLogicalStopId,
  });

  return { app, searchStops, getDepartures, getDeparturesByLogicalStopId };
};

describe("api routes", () => {
  it("rejects invalid search query", async () => {
    const { app } = buildTestApp();

    const response = await app.request("/v1/rouen/stops/search?q=a");
    const json = await response.json();

    expect(response.status).toBe(400);
    expect(json.error).toBe("Invalid query parameters");
  });

  it("returns search payload", async () => {
    const { app, searchStops } = buildTestApp();

    const response = await app.request("/v1/rouen/stops/search?q=gare&limit=5");
    const json = await response.json();

    expect(response.status).toBe(200);
    expect(json.count).toBe(1);
    expect(searchStops).toHaveBeenCalledTimes(1);
  });

  it("returns departures payload", async () => {
    const { app, getDepartures } = buildTestApp();

    const response = await app.request(
      "/v1/rouen/stops/TAE:1131/departures?limit=2&maxMinutes=120&lines=t2,%20f,,T2",
    );
    const json = await response.json();

    expect(response.status).toBe(200);
    expect(Array.isArray(json.departures)).toBe(true);
    expect(getDepartures).toHaveBeenCalledTimes(1);
    expect(getDepartures).toHaveBeenCalledWith(
      expect.objectContaining({
        stopId: "TAE:1131",
        limit: 2,
        maxMinutesAhead: 120,
        lines: ["T2", "F"],
      }),
    );
  });

  it("returns logical-stop departures payload", async () => {
    const { app, getDeparturesByLogicalStopId } = buildTestApp();

    const response = await app.request(
      "/v1/rouen/logical-stops/94220/departures?limit=2&maxMinutes=120&lines=t2,%20f,,T2",
    );
    const json = await response.json();

    expect(response.status).toBe(200);
    expect(Array.isArray(json.departures)).toBe(true);
    expect(getDeparturesByLogicalStopId).toHaveBeenCalledTimes(1);
    expect(getDeparturesByLogicalStopId).toHaveBeenCalledWith(
      expect.objectContaining({
        logicalStopId: 94220,
        limit: 2,
        maxMinutesAhead: 120,
        lines: ["T2", "F"],
      }),
    );
  });

  it("maps departures errors to 502", async () => {
    const app = createApp({
      config: {
        host: "127.0.0.1",
        port: 3000,
        rouenStaticGtfsUrl: "https://example.com/gtfs.zip",
        rouenTripUpdatesUrls: ["https://example.com/tripupdates.pb"],
        rouenStaticCacheTtlMinutes: 60,
      },
      searchStops: async (_args: unknown) => ({ count: 0, results: [] }),
      getDepartures: async (_args: unknown) => {
        throw new Error("feed down");
      },
      getDeparturesByLogicalStopId: async (_args: unknown) => ({
        generatedAtUnix: 0,
        feedTimestampUnix: 0,
        stop: null,
        logicalStopId: 123,
        departures: [],
      }),
    });

    const response = await app.request("/v1/rouen/stops/TAE:1131/departures");
    const json = await response.json();

    expect(response.status).toBe(502);
    expect(json.error).toBe("Failed to load departures");
  });
});
