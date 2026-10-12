import { beforeEach, describe, expect, it } from "vitest";
import { getProgress, recentSeries, saveProgress } from "./history";

class MemoryStorage {
  private items = new Map<string, string>();
  getItem(key: string) {
    return this.items.get(key) ?? null;
  }
  setItem(key: string, value: string) {
    this.items.set(key, value);
  }
}

describe("guest history", () => {
  beforeEach(() => {
    Object.assign(globalThis, { localStorage: new MemoryStorage() });
  });

  it("remembers the latest chapter per series and clamps position", () => {
    saveProgress("tower", "12.5", 1.7, new Date("2026-09-26T10:00:00Z"));
    expect(getProgress("tower")).toEqual({
      number: "12.5",
      position: 1,
      updatedAt: "2026-09-26T10:00:00.000Z",
    });
    expect(getProgress("other")).toBeUndefined();
  });

  it("lists recent series with what Continue reading shows", () => {
    const tower = { title: "The Tower", type: "MANHWA" as const, cover: null };
    saveProgress("tower", "3", 0.5, new Date("2026-09-26T10:00:00Z"), tower);
    saveProgress("tide", "1", 0, new Date("2026-09-27T10:00:00Z"));
    // A later save without series details keeps the ones already stored.
    saveProgress("tower", "4", 0, new Date("2026-09-28T10:00:00Z"));

    expect(recentSeries(5).map((entry) => [entry.slug, entry.number, entry.series?.title])).toEqual(
      [
        ["tower", "4", "The Tower"],
        ["tide", "1", undefined],
      ],
    );
    expect(recentSeries(1)).toHaveLength(1);
  });

  it("survives corrupt or missing storage", () => {
    localStorage.setItem("canreadit:history:v1", "{not json");
    expect(getProgress("tower")).toBeUndefined();
    Object.assign(globalThis, { localStorage: undefined });
    expect(() => saveProgress("tower", "1", 0)).not.toThrow();
    expect(getProgress("tower")).toBeUndefined();
  });
});
