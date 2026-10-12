import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { backKey, notePopstate, restoreOnBack, saveForBack } from "./restore-on-back";

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

function onEntry(key: string | undefined) {
  Object.assign(globalThis, { navigation: key ? { currentEntry: { key } } : undefined });
}

describe("restore on back", () => {
  beforeEach(() => {
    Object.assign(globalThis, { sessionStorage: new MemoryStorage() });
    notePopstate("", 0);
  });
  afterEach(() => onEntry(undefined));

  it("ties state to the history entry when the Navigation API exists", () => {
    onEntry("entry-1");
    const first = backKey("/browse?type=NOVEL");
    saveForBack(first, { loaded: 3 }, NOW);

    // Back to the same entry: restored. A new visit (new entry) or another URL: not.
    expect(restoreOnBack(backKey("/browse?type=NOVEL"), NOW + 10)).toEqual({ loaded: 3 });
    expect(restoreOnBack(backKey("/browse"), NOW + 10)).toBeUndefined();
    onEntry("entry-2");
    expect(restoreOnBack(backKey("/browse?type=NOVEL"), NOW + 10)).toBeUndefined();
  });

  it("falls back to a recent popstate without the Navigation API", () => {
    const key = backKey("/browse?type=NOVEL");
    saveForBack(key, { loaded: 3 }, NOW);

    expect(restoreOnBack(key, NOW + 10)).toBeUndefined();
    notePopstate("/browse?type=NOVEL", NOW + 5_000);
    expect(restoreOnBack(key, NOW + 5_100)).toEqual({ loaded: 3 });
    expect(restoreOnBack(key, NOW + 60_000)).toBeUndefined();
  });

  it("forgets old or corrupt state", () => {
    onEntry("entry-1");
    const key = backKey("/browse");
    saveForBack(key, { loaded: 1 }, NOW);
    expect(restoreOnBack(key, NOW + 31 * 60_000)).toBeUndefined();

    sessionStorage.setItem("canreadit:restore:entry-1:/browse", "{oops");
    expect(restoreOnBack(key, NOW)).toBeUndefined();
  });
});
