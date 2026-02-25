import * as v from "valibot";

const defaultTripUpdatesUrls = [
  "https://api.mrn.cityway.fr/dataflow/horaire-tc-tr/download?provider=TCAR&dataFormat=gtfs-rt",
  "https://api.mrn.cityway.fr/dataflow/horaire-tc-tr/download?provider=TNI&dataFormat=gtfs-rt",
  "https://api.mrn.cityway.fr/dataflow/horaire-tc-tr/download?provider=TAE&dataFormat=gtfs-rt",
];

const defaultStaticGtfsUrl =
  "https://api.mrn.cityway.fr/dataflow/offre-tc/download?provider=ASTUCE&dataFormat=gtfs&dataProfil=ASTUCE";

const toInt = (defaultValue: number, minValue: number) =>
  v.pipe(
    v.optional(v.string(), String(defaultValue)),
    v.transform((input) => Number(input)),
    v.number(),
    v.integer(),
    v.minValue(minValue),
  );

const envSchema = v.object({
  HOST: v.pipe(v.optional(v.string(), "0.0.0.0"), v.trim(), v.minLength(1)),
  PORT: toInt(3000, 1),
  ROUTEN_STATIC_GTFS_URL: v.pipe(
    v.optional(v.string(), defaultStaticGtfsUrl),
    v.url(),
  ),
  ROUTEN_TRIP_UPDATES_URLS: v.pipe(
    v.optional(v.string(), defaultTripUpdatesUrls.join(",")),
    v.transform((value) =>
      value
        .split(",")
        .map((url) => url.trim())
        .filter((url) => url.length > 0),
    ),
    v.array(v.pipe(v.string(), v.url())),
  ),
  ROUTEN_STATIC_CACHE_TTL_MINUTES: toInt(720, 5),
});

type EnvInput = Partial<Record<string, string | undefined>>;

export const parseEnv = (input: EnvInput) => {
  const parsed = v.parse(envSchema, input);

  return {
    host: parsed.HOST,
    port: parsed.PORT,
    rouenStaticGtfsUrl: parsed.ROUTEN_STATIC_GTFS_URL,
    rouenTripUpdatesUrls: parsed.ROUTEN_TRIP_UPDATES_URLS,
    rouenStaticCacheTtlMinutes: parsed.ROUTEN_STATIC_CACHE_TTL_MINUTES,
  };
};

export const env = parseEnv(Bun.env);
