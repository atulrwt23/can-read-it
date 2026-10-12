import { describe, expect, it } from "vitest";
import { defaultSort, hasFilters, parseBrowseParams, toSearchParams } from "./browse-params";

describe("parseBrowseParams", () => {
  it("keeps valid values", () => {
    expect(
      parseBrowseParams({
        q: " tower ",
        type: "NOVEL",
        status: "HIATUS",
        sort: "TITLE",
        genres: "action,sci-fi",
      }),
    ).toEqual({
      q: "tower",
      type: "NOVEL",
      status: "HIATUS",
      sort: "TITLE",
      genres: ["action", "sci-fi"],
    });
  });

  it("drops invalid values", () => {
    expect(
      parseBrowseParams({
        q: "   ",
        type: "COMIC",
        status: ["ONGOING", "x"],
        sort: "random",
        genres: "Bad Slug,ok",
      }),
    ).toEqual({
      q: undefined,
      type: undefined,
      status: "ONGOING",
      sort: undefined,
      genres: ["ok"],
    });
  });

  it("round-trips through the URL", () => {
    const params = {
      q: "a b",
      type: "MANHWA" as const,
      genres: ["x", "y"],
      sort: "NEWEST" as const,
    };
    expect(parseBrowseParams(Object.fromEntries(toSearchParams(params)))).toEqual({
      ...params,
      status: undefined,
    });
    expect(toSearchParams({ sort: "UPDATED" }).toString()).toBe("");
  });

  it("orders searches by best match unless another order is chosen", () => {
    expect(defaultSort({ q: "tower" })).toBe("RELEVANCE");
    expect(defaultSort({})).toBe("UPDATED");
    expect(toSearchParams({ q: "tower", sort: "RELEVANCE" }).toString()).toBe("q=tower");
    expect(toSearchParams({ q: "tower", sort: "UPDATED" }).toString()).toBe("q=tower&sort=UPDATED");
    // Clearing the search drops an order that needs it.
    expect(toSearchParams({ sort: "RELEVANCE" }).toString()).toBe("");
    expect(parseBrowseParams({ sort: "RELEVANCE" }).sort).toBeUndefined();
    expect(parseBrowseParams({ q: "x", sort: "RELEVANCE" }).sort).toBe("RELEVANCE");
  });

  it("knows when there is something to clear", () => {
    expect(hasFilters({})).toBe(false);
    expect(hasFilters({ sort: "UPDATED" })).toBe(false);
    expect(hasFilters({ sort: "POPULAR" })).toBe(true);
    expect(hasFilters({ genres: ["action"] })).toBe(true);
    expect(hasFilters({ q: "x" })).toBe(true);
  });
});
