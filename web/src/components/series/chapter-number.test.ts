import { describe, expect, it } from "vitest";
import { CHAPTER_NUMBER } from "./chapter-list";

describe("jump to chapter", () => {
  it("accepts chapter numbers the API understands", () => {
    for (const ok of ["1", "12", "12.5", "012.50", "999999"])
      expect(CHAPTER_NUMBER.test(ok)).toBe(true);
    for (const bad of ["", "-1", "1.", "1.234", "12a", "../1", "1e3"]) {
      expect(CHAPTER_NUMBER.test(bad)).toBe(false);
    }
  });
});
