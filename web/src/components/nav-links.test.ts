import { describe, expect, it } from "vitest";
import { isCurrent } from "./nav-links";

describe("isCurrent", () => {
  it("matches home only on the home page", () => {
    expect(isCurrent("/", "/")).toBe(true);
    expect(isCurrent("/", "/browse")).toBe(false);
  });

  it("matches a section and its sub-pages, not look-alikes", () => {
    expect(isCurrent("/browse", "/browse")).toBe(true);
    expect(isCurrent("/library", "/library/history")).toBe(true);
    expect(isCurrent("/browse", "/browsers")).toBe(false);
  });
});
