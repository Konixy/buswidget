import { Hono } from "hono";
import { logger } from "hono/logger";
import { networkInterfaces } from "node:os";
import * as v from "valibot";

import { env } from "./env";
import {
  getRouenDeparturesForLogicalStop,
  getRouenDeparturesForStop,
  searchRouenStops,
} from "./lib/rouen";

const parseIntQuery = (defaultValue: number, minValue: number, maxValue: number) =>
  v.pipe(
    v.optional(v.string(), String(defaultValue)),
    v.transform((value) => Number(value)),
    v.number(),
    v.integer(),
    v.minValue(minValue),
    v.maxValue(maxValue),
  );

const parseLinesQuery = () =>
  v.pipe(
    v.optional(v.string(), ""),
    v.transform((value) =>
      Array.from(
        new Set(
          value
            .split(",")
            .map((line) => line.trim())
            .filter((line) => line.length > 0)
            .map((line) => line.toLocaleUpperCase("fr-FR")),
        ),
      ),
    ),
    v.array(v.string()),
    v.maxLength(20),
  );

const querySearchSchema = v.object({
  q: v.pipe(v.string(), v.trim(), v.minLength(2)),
  limit: parseIntQuery(20, 1, 50),
});

const departuresQuerySchema = v.object({
  limit: parseIntQuery(8, 1, 20),
  maxMinutes: parseIntQuery(90, 1, 240),
  lines: parseLinesQuery(),
});

type AppDeps = {
  config: typeof env;
  searchStops: (args: { query: string; limit: number; staticGtfsUrl: string; staticCacheTtlMinutes: number }) => Promise<unknown>;
  getDepartures: (args: {
    stopId: string;
    maxMinutesAhead: number;
    limit: number;
    lines: string[];
    staticGtfsUrl: string;
    staticCacheTtlMinutes: number;
    tripUpdatesUrls: string[];
  }) => Promise<unknown>;
  getDeparturesByLogicalStopId: (args: {
    logicalStopId: number;
    maxMinutesAhead: number;
    limit: number;
    lines: string[];
    staticGtfsUrl: string;
    staticCacheTtlMinutes: number;
    tripUpdatesUrls: string[];
  }) => Promise<unknown>;
};

const formatIssues = (issues: v.BaseIssue<unknown>[]) =>
  issues.map((issue) => ({
    message: issue.message,
    path: v.getDotPath(issue),
  }));

const withTimingHeaders = (response: Response, metricName: string, startedAtMs: number) => {
  const durationMs = Math.max(0, performance.now() - startedAtMs);
  response.headers.append("Server-Timing", `${metricName};dur=${durationMs.toFixed(1)}`);
  response.headers.set("X-Response-Time-Ms", durationMs.toFixed(1));
  return response;
};

export const createApp = (deps: AppDeps) => {
  const app = new Hono();

  app.use(logger());

  app.get("/health", (c) => {
    return c.json({
      ok: true,
      service: "buswidget-api",
      city: "Rouen",
      timestamp: new Date().toISOString(),
    });
  });

  app.get("/v1/rouen/stops/search", async (c) => {
    const startedAtMs = performance.now();
    const parsed = v.safeParse(querySearchSchema, c.req.query());
    if (!parsed.success) {
      return withTimingHeaders(c.json(
        {
          error: "Invalid query parameters",
          details: formatIssues(parsed.issues),
        },
        400,
      ), "search", startedAtMs);
    }

    const response = await deps.searchStops({
      query: parsed.output.q,
      limit: parsed.output.limit,
      staticGtfsUrl: deps.config.rouenStaticGtfsUrl,
      staticCacheTtlMinutes: deps.config.rouenStaticCacheTtlMinutes,
    });

    return withTimingHeaders(c.json(response), "search", startedAtMs);
  });

  app.get("/v1/rouen/stops/:stopId/departures", async (c) => {
    const startedAtMs = performance.now();
    const stopId = c.req.param("stopId").trim();
    if (!stopId) {
      return withTimingHeaders(c.json({ error: "stopId is required" }, 400), "departures", startedAtMs);
    }

    const parsed = v.safeParse(departuresQuerySchema, c.req.query());
    if (!parsed.success) {
      return withTimingHeaders(c.json(
        {
          error: "Invalid query parameters",
          details: formatIssues(parsed.issues),
        },
        400,
      ), "departures", startedAtMs);
    }

    try {
      const data = await deps.getDepartures({
        stopId,
        limit: parsed.output.limit,
        maxMinutesAhead: parsed.output.maxMinutes,
        lines: parsed.output.lines,
        staticGtfsUrl: deps.config.rouenStaticGtfsUrl,
        staticCacheTtlMinutes: deps.config.rouenStaticCacheTtlMinutes,
        tripUpdatesUrls: deps.config.rouenTripUpdatesUrls,
      });
      return withTimingHeaders(c.json(data), "departures", startedAtMs);
    } catch (error) {
      const message = error instanceof Error ? error.message : "Unknown error";
      return withTimingHeaders(c.json(
        {
          error: "Failed to load departures",
          message,
        },
        502,
      ), "departures", startedAtMs);
    }
  });

  app.get("/v1/rouen/logical-stops/:logicalStopId/departures", async (c) => {
    const startedAtMs = performance.now();
    const logicalStopIdValue = c.req.param("logicalStopId").trim();
    const logicalStopId = Number(logicalStopIdValue);
    if (!logicalStopIdValue || !Number.isInteger(logicalStopId) || logicalStopId <= 0) {
      return withTimingHeaders(
        c.json({ error: "logicalStopId must be a positive integer" }, 400),
        "departures_logical",
        startedAtMs,
      );
    }

    const parsed = v.safeParse(departuresQuerySchema, c.req.query());
    if (!parsed.success) {
      return withTimingHeaders(
        c.json(
          {
            error: "Invalid query parameters",
            details: formatIssues(parsed.issues),
          },
          400,
        ),
        "departures_logical",
        startedAtMs,
      );
    }

    try {
      const data = await deps.getDeparturesByLogicalStopId({
        logicalStopId,
        limit: parsed.output.limit,
        maxMinutesAhead: parsed.output.maxMinutes,
        lines: parsed.output.lines,
        staticGtfsUrl: deps.config.rouenStaticGtfsUrl,
        staticCacheTtlMinutes: deps.config.rouenStaticCacheTtlMinutes,
        tripUpdatesUrls: deps.config.rouenTripUpdatesUrls,
      });
      return withTimingHeaders(c.json(data), "departures_logical", startedAtMs);
    } catch (error) {
      const message = error instanceof Error ? error.message : "Unknown error";
      return withTimingHeaders(
        c.json(
          {
            error: "Failed to load departures",
            message,
          },
          502,
        ),
        "departures_logical",
        startedAtMs,
      );
    }
  });

  return app;
};

const app = createApp({
  config: env,
  searchStops: searchRouenStops,
  getDepartures: getRouenDeparturesForStop,
  getDeparturesByLogicalStopId: getRouenDeparturesForLogicalStop,
});

const getLanUrls = (host: string, port: number): string[] => {
  if (host !== "0.0.0.0" && host !== "::") {
    return [`http://${host}:${port}`];
  }

  const urls = new Set<string>();
  const interfaces = networkInterfaces();

  for (const iface of Object.values(interfaces)) {
    for (const addr of iface ?? []) {
      if (addr.internal) {
        continue;
      }

      if (addr.family === "IPv4") {
        urls.add(`http://${addr.address}:${port}`);
      }
    }
  }

  return Array.from(urls).sort();
};

if (import.meta.main) {
  const urls = getLanUrls(env.host, env.port);
  const displayHost = env.host === "0.0.0.0" ? "all interfaces" : env.host;
  console.log(`[api] Listening on ${displayHost}:${env.port}`);
  for (const url of urls) {
    console.log(`[api] Reachable at ${url}`);
  }
}

export default {
  fetch: app.fetch,
  hostname: env.host,
  port: env.port,
};
