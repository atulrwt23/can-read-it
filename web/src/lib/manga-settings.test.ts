import { describe, expect, it } from "vitest";
import { sanitizeMangaSettings } from "./manga-settings";

describe("manga settings", () => {
  it("keeps known widths and falls back to standard", () => {
    expect(sanitizeMangaSettings({ width: "wide" })).toEqual({ width: "wide" });
    expect(sanitizeMangaSettings({ width: "huge" })).toEqual({ width: "standard" });
    expect(sanitizeMangaSettings(null)).toEqual({ width: "standard" });
  });
});
