import { describe, expect, it } from "bun:test";

import { parseEnv } from "./env";

describe("parseEnv", () => {
  it("uses defaults when values are missing", () => {
    const env = parseEnv({});

    expect(env.host).toBe("0.0.0.0");
    expect(env.port).toBe(3000);
    expect(env.rouenStaticCacheTtlMinutes).toBe(720);
    expect(env.rouenTripUpdatesUrls.length).toBe(3);
  });

  it("parses custom values", () => {
    const env = parseEnv({
      HOST: "127.0.0.1",
      PORT: "4010",
      ROUTEN_STATIC_CACHE_TTL_MINUTES: "60",
      ROUTEN_TRIP_UPDATES_URLS: "https://example.com/a, https://example.com/b",
    });

    expect(env.host).toBe("127.0.0.1");
    expect(env.port).toBe(4010);
    expect(env.rouenStaticCacheTtlMinutes).toBe(60);
    expect(env.rouenTripUpdatesUrls).toEqual([
      "https://example.com/a",
      "https://example.com/b",
    ]);
  });

  it("throws on invalid url", () => {
    expect(() =>
      parseEnv({
        ROUTEN_STATIC_GTFS_URL: "not-a-url",
      }),
    ).toThrow();
  });
});
