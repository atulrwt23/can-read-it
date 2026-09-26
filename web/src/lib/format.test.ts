import { describe, expect, it } from "vitest";
import { compactNumber, isNew, label, relativeTime } from "./format";

const now = new Date("2026-09-26T12:00:00Z");

describe("relativeTime", () => {
  it.each([
    ["2026-09-26T11:59:30Z", "just now"],
    ["2026-09-26T11:20:00Z", "40m ago"],
    ["2026-09-26T09:00:00Z", "3h ago"],
    ["2026-09-24T12:00:00Z", "2d ago"],
    ["2026-09-12T12:00:00Z", "2w ago"],
    ["2026-06-01T12:00:00Z", "Jun 1, 2026"],
  ])("formats %s as %s", (iso, expected) => {
    expect(relativeTime(iso, now)).toBe(expected);
  });
});

describe("isNew", () => {
  it("is true within a day", () => {
    expect(isNew("2026-09-25T13:00:00Z", now)).toBe(true);
    expect(isNew("2026-09-25T11:00:00Z", now)).toBe(false);
  });
});

describe("compactNumber and label", () => {
  it("formats", () => {
    expect(compactNumber(4545)).toBe("4.5K");
    expect(compactNumber(12)).toBe("12");
    expect(label("EARLY_ACCESS")).toBe("EARLY_ACCESS");
    expect(label("MANHWA")).toBe("Manhwa");
  });
});
