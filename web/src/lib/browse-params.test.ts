import { describe, expect, it } from "vitest";
import { parseBrowseParams, toSearchParams } from "./browse-params";

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
});
