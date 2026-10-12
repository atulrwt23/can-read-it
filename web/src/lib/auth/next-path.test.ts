import { describe, expect, it } from "vitest";
import { safeNextPath } from "./next-path";

describe("safeNextPath", () => {
  it("keeps paths on this site", () => {
    expect(safeNextPath("/account")).toBe("/account");
    expect(safeNextPath("/series/a-b/chapter/2?x=1#top")).toBe("/series/a-b/chapter/2?x=1#top");
  });

  it("sends everything else home", () => {
    for (const value of [
      null,
      undefined,
      "",
      "https://evil.test",
      "//evil.test",
      "/\\evil.test",
      "javascript:alert(1)",
      "account",
      "/\n/evil.test",
    ]) {
      expect(safeNextPath(value), String(value)).toBe("/");
    }
  });

  it("never returns to the sign-in pages", () => {
    expect(safeNextPath("/login")).toBe("/");
    expect(safeNextPath("/signup?next=/account")).toBe("/");
    expect(safeNextPath("/login/reset")).toBe("/");
    expect(safeNextPath("/loginx")).toBe("/loginx");
  });
});
