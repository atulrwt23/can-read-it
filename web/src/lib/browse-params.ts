import type { BrowseParams } from "@/lib/api/server";

const TYPES = ["MANHWA", "NOVEL"] as const;
const STATUSES = ["ONGOING", "COMPLETED", "HIATUS"] as const;
const SORTS = ["RELEVANCE", "UPDATED", "POPULAR", "NEWEST", "TITLE"] as const;

type Raw = Record<string, string | string[] | undefined>;

function first(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}

function oneOf<T extends string>(allowed: readonly T[], value: string | undefined): T | undefined {
  return allowed.find((candidate) => candidate === value);
}

export type BrowseSort = NonNullable<BrowseParams["sort"]>;

/** The API's order when none is given: best match when searching, latest update otherwise. */
export function defaultSort(params: Pick<BrowseParams, "q">): BrowseSort {
  return params.q ? "RELEVANCE" : "UPDATED";
}

/** Turns URL search params into API browse params, dropping anything invalid. */
export function parseBrowseParams(raw: Raw): BrowseParams {
  const q = first(raw.q)?.trim().slice(0, 100) || undefined;
  const genres = (first(raw.genres) ?? "")
    .split(",")
    .filter((slug) => /^[a-z0-9]+(-[a-z0-9]+)*$/.test(slug))
    .slice(0, 10);
  const sort = oneOf(SORTS, first(raw.sort));
  return {
    q,
    type: oneOf(TYPES, first(raw.type)),
    status: oneOf(STATUSES, first(raw.status)),
    // Relevance needs search text; the default order is left implicit.
    sort: sort === "RELEVANCE" && !q ? undefined : sort,
    genres: genres.length > 0 ? genres : undefined,
  };
}

/** Whether anything narrows or reorders the list (so "Clear filters" has something to clear). */
export function hasFilters(params: BrowseParams): boolean {
  return Boolean(
    params.q ||
      params.type ||
      params.status ||
      params.genres?.length ||
      (params.sort && params.sort !== defaultSort(params)),
  );
}

/** The inverse, for links and router updates. Empty values are left out. */
export function toSearchParams(params: BrowseParams): URLSearchParams {
  const search = new URLSearchParams();
  if (params.q) search.set("q", params.q);
  if (params.type) search.set("type", params.type);
  if (params.status) search.set("status", params.status);
  if (params.genres?.length) search.set("genres", params.genres.join(","));
  if (
    params.sort &&
    params.sort !== defaultSort(params) &&
    (params.sort !== "RELEVANCE" || params.q)
  ) {
    search.set("sort", params.sort);
  }
  return search;
}
