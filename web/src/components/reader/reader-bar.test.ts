import { describe, expect, it } from "vitest";
import { withChapter } from "./reader-bar";

describe("chapter picker", () => {
  it("adds the open chapter in number order when the list doesn't reach it yet", () => {
    expect(withChapter(["1", "2", "3"], "2")).toEqual(["1", "2", "3"]);
    expect(withChapter(["1", "2", "10"], "9.5")).toEqual(["1", "2", "9.5", "10"]);
    expect(withChapter(["1", "2"], "250")).toEqual(["1", "2", "250"]);
  });
});
