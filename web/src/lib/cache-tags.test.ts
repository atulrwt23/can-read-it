import { describe, expect, it } from "vitest";
import { cacheTags, parseRevalidateTags } from "./cache-tags";

describe("parseRevalidateTags", () => {
  it("accepts the tags the API sends", () => {
    expect(
      parseRevalidateTags({
        tags: [cacheTags.home, cacheTags.seriesLists, cacheTags.series("solo-tower")],
      }),
    ).toEqual(["home", "series-lists", "series:solo-tower"]);
  });

  it("drops duplicates", () => {
    expect(parseRevalidateTags({ tags: ["home", "home"] })).toEqual(["home"]);
  });

  it.each([
    null,
    "home",
    {},
    { tags: [] },
    { tags: "home" },
    { tags: [42] },
    { tags: ["Home"] },
    { tags: ["series:../x"] },
    { tags: ["a b"] },
    { tags: Array.from({ length: 21 }, (_, i) => `t${i}`) },
  ])("rejects %j", (body) => {
    expect(parseRevalidateTags(body)).toBeNull();
  });
});
