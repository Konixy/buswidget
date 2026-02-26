import { normalizeForSearch } from "./helpers";
import { loadRouenStaticData } from "./static-data";
import type { SearchableStopEntry } from "./types";

export const searchRouenStops = async (args: {
  query: string;
  limit: number;
  staticGtfsUrl: string;
  staticCacheTtlMinutes: number;
}) => {
  const staticData = await loadRouenStaticData(
    args.staticGtfsUrl,
    args.staticCacheTtlMinutes,
  );

  const q = normalizeForSearch(args.query.trim());
  const rankedStops: {
    entry: SearchableStopEntry;
    startsWithName: boolean;
    startsWithCode: boolean;
  }[] = [];

  for (const entry of staticData.searchableStops) {
    const isMatch =
      entry.normalizedName.includes(q) ||
      entry.normalizedId.includes(q) ||
      entry.normalizedCode.includes(q);
    if (!isMatch) {
      continue;
    }

    rankedStops.push({
      entry,
      startsWithName: entry.normalizedName.startsWith(q),
      startsWithCode: entry.normalizedCode.startsWith(q),
    });
  }

  rankedStops.sort((a, b) => {
    if (a.startsWithName !== b.startsWithName) {
      return a.startsWithName ? -1 : 1;
    }

    if (a.entry.hasKnownService !== b.entry.hasKnownService) {
      return a.entry.hasKnownService ? -1 : 1;
    }

    if (a.entry.isBoardingStop !== b.entry.isBoardingStop) {
      return a.entry.isBoardingStop ? -1 : 1;
    }

    if (a.entry.providerPriority !== b.entry.providerPriority) {
      return a.entry.providerPriority - b.entry.providerPriority;
    }

    if (a.entry.lineHintCount !== b.entry.lineHintCount) {
      return b.entry.lineHintCount - a.entry.lineHintCount;
    }

    if (a.startsWithCode !== b.startsWithCode) {
      return a.startsWithCode ? -1 : 1;
    }

    const nameCmp = a.entry.stop.name.localeCompare(b.entry.stop.name, "fr", {
      sensitivity: "base",
      numeric: true,
    });
    if (nameCmp !== 0) {
      return nameCmp;
    }

    return a.entry.stop.id.localeCompare(b.entry.stop.id, "fr", {
      sensitivity: "base",
      numeric: true,
    });
  });

  const results = rankedStops.slice(0, args.limit).map((item) => item.entry.stop);

  return {
    count: results.length,
    results,
  };
};
