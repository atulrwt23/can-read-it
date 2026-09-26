import type { BrowseParams } from "@/lib/api/server";

const TYPES = ["MANHWA", "NOVEL"] as const;
const STATUSES = ["ONGOING", "COMPLETED", "HIATUS"] as const;
const SORTS = ["UPDATED", "NEWEST", "TITLE"] as const;

type Raw = Record<string, string | string[] | undefined>;

function first(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}

function oneOf<T extends string>(allowed: readonly T[], value: string | undefined): T | undefined {
  return allowed.find((candidate) => candidate === value);
}

/** Turns URL search params into API browse params, dropping anything invalid. */
export function parseBrowseParams(raw: Raw): BrowseParams {
  const q = first(raw.q)?.trim().slice(0, 100);
  const genres = (first(raw.genres) ?? "")
    .split(",")
    .filter((slug) => /^[a-z0-9]+(-[a-z0-9]+)*$/.test(slug))
    .slice(0, 10);
  return {
    q: q || undefined,
    type: oneOf(TYPES, first(raw.type)),
    status: oneOf(STATUSES, first(raw.status)),
    sort: oneOf(SORTS, first(raw.sort)),
    genres: genres.length > 0 ? genres : undefined,
  };
}

/** The inverse, for links and router updates. Empty values are left out. */
export function toSearchParams(params: BrowseParams): URLSearchParams {
  const search = new URLSearchParams();
  if (params.q) search.set("q", params.q);
  if (params.type) search.set("type", params.type);
  if (params.status) search.set("status", params.status);
  if (params.genres?.length) search.set("genres", params.genres.join(","));
  if (params.sort && params.sort !== "UPDATED") search.set("sort", params.sort);
  return search;
}
