import { beforeEach, describe, expect, it } from "vitest";
import { getProgress, saveProgress } from "./history";

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

  it("survives corrupt or missing storage", () => {
    localStorage.setItem("canreadit:history:v1", "{not json");
    expect(getProgress("tower")).toBeUndefined();
    Object.assign(globalThis, { localStorage: undefined });
    expect(() => saveProgress("tower", "1", 0)).not.toThrow();
    expect(getProgress("tower")).toBeUndefined();
  });
});
