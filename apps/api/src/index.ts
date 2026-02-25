import { Hono } from "hono";
import { networkInterfaces } from "node:os";
import * as v from "valibot";

import { env } from "./env";
import { getRouenDeparturesForStop, searchRouenStops } from "./lib/rouen-astuce";

const parseIntQuery = (defaultValue: number, minValue: number, maxValue: number) =>
  v.pipe(
    v.optional(v.string(), String(defaultValue)),
    v.transform((value) => Number(value)),
    v.number(),
    v.integer(),
    v.minValue(minValue),
    v.maxValue(maxValue),
  );

const querySearchSchema = v.object({
  q: v.pipe(v.string(), v.trim(), v.minLength(2)),
  limit: parseIntQuery(20, 1, 50),
});

const departuresQuerySchema = v.object({
  limit: parseIntQuery(8, 1, 20),
  maxMinutes: parseIntQuery(90, 1, 240),
});

type AppDeps = {
  config: typeof env;
  searchStops: (args: {
    query: string;
    limit: number;
    staticGtfsUrl: string;
    staticCacheTtlMinutes: number;
  }) => Promise<unknown>;
  getDepartures: (args: {
    stopId: string;
    maxMinutesAhead: number;
    limit: number;
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

export const createApp = (deps: AppDeps) => {
  const app = new Hono();

  app.get("/health", (c) => {
    return c.json({
      ok: true,
      service: "buswidget-api",
      city: "Rouen",
      timestamp: new Date().toISOString(),
    });
  });

  app.get("/v1/rouen/stops/search", async (c) => {
    const parsed = v.safeParse(querySearchSchema, c.req.query());
    if (!parsed.success) {
      return c.json(
        {
          error: "Invalid query parameters",
          details: formatIssues(parsed.issues),
        },
        400,
      );
    }

    const response = await deps.searchStops({
      query: parsed.output.q,
      limit: parsed.output.limit,
      staticGtfsUrl: deps.config.rouenStaticGtfsUrl,
      staticCacheTtlMinutes: deps.config.rouenStaticCacheTtlMinutes,
    });

    return c.json(response);
  });

  app.get("/v1/rouen/stops/:stopId/departures", async (c) => {
    const stopId = c.req.param("stopId").trim();
    if (!stopId) {
      return c.json({ error: "stopId is required" }, 400);
    }

    const parsed = v.safeParse(departuresQuerySchema, c.req.query());
    if (!parsed.success) {
      return c.json(
        {
          error: "Invalid query parameters",
          details: formatIssues(parsed.issues),
        },
        400,
      );
    }

    try {
      const data = await deps.getDepartures({
        stopId,
        limit: parsed.output.limit,
        maxMinutesAhead: parsed.output.maxMinutes,
        staticGtfsUrl: deps.config.rouenStaticGtfsUrl,
        staticCacheTtlMinutes: deps.config.rouenStaticCacheTtlMinutes,
        tripUpdatesUrls: deps.config.rouenTripUpdatesUrls,
      });
      return c.json(data);
    } catch (error) {
      const message = error instanceof Error ? error.message : "Unknown error";
      return c.json(
        {
          error: "Failed to load departures",
          message,
        },
        502,
      );
    }
  });

  return app;
};

const app = createApp({
  config: env,
  searchStops: searchRouenStops,
  getDepartures: getRouenDeparturesForStop,
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
