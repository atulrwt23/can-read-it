import { beforeEach, describe, expect, it } from "vitest";
import { notePopstate, restoreOnBack, saveForBack } from "./restore-on-back";

class MemoryStorage {
  private items = new Map<string, string>();
  getItem(key: string) {
    return this.items.get(key) ?? null;
  }
  setItem(key: string, value: string) {
    this.items.set(key, value);
  }
}

const NOW = 1_800_000_000_000;

describe("restore on back", () => {
  beforeEach(() => {
    Object.assign(globalThis, { sessionStorage: new MemoryStorage() });
    notePopstate("", 0);
  });

  it("gives state back only after a back navigation to the same URL", () => {
    saveForBack("/browse?type=NOVEL", { loaded: 3 }, NOW);

    expect(restoreOnBack("/browse?type=NOVEL", NOW + 10)).toBeUndefined();

    notePopstate("/browse?type=NOVEL", NOW + 5_000);
    expect(restoreOnBack("/browse?type=NOVEL", NOW + 5_100)).toEqual({ loaded: 3 });
    expect(restoreOnBack("/browse", NOW + 5_100)).toBeUndefined();
    // A visit long after the popstate is a fresh one.
    expect(restoreOnBack("/browse?type=NOVEL", NOW + 60_000)).toBeUndefined();
  });

  it("forgets old or corrupt state", () => {
    saveForBack("/browse", { loaded: 1 }, NOW);
    notePopstate("/browse", NOW + 31 * 60_000);
    expect(restoreOnBack("/browse", NOW + 31 * 60_000)).toBeUndefined();

    sessionStorage.setItem("canreadit:restore:/browse", "{oops");
    notePopstate("/browse", NOW);
    expect(restoreOnBack("/browse", NOW)).toBeUndefined();
  });
});
