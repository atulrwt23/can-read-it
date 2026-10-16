import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const api = vi.fn();
let signedIn = false;

vi.mock("@/lib/auth/client", () => ({
  ApiError: class ApiError extends Error {
    constructor(
      readonly status: number,
      readonly code: string,
    ) {
      super(code);
    }
  },
  api: (...args: unknown[]) => api(...args),
  hasSignedInHint: () => signedIn,
  setSignedInHint: (value: boolean) => {
    signedIn = value;
  },
}));

const { chapterProgress, importGuestHistory, SYNC_INTERVAL_MS, seriesReading } = await import(
  "./store"
);
const guest = await import("@/lib/history");

class MemoryStorage {
  private items = new Map<string, string>();
  getItem(key: string) {
    return this.items.get(key) ?? null;
  }
  setItem(key: string, value: string) {
    this.items.set(key, value);
  }
  removeItem(key: string) {
    this.items.delete(key);
  }
}

const meta = { title: "Tower", type: "MANHWA" as const, cover: null };

describe("reading store", () => {
  beforeEach(() => {
    Object.assign(globalThis, { localStorage: new MemoryStorage() });
    api.mockReset();
    signedIn = false;
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("keeps a guest's progress and read marks in the browser", async () => {
    const progress = chapterProgress("tower", "3", meta);
    progress.update(0.4);
    expect(guest.getProgress("tower")).toMatchObject({ number: "3", position: 0.4 });
    progress.finish();
    expect(guest.readChapters("tower")).toEqual(new Set(["3"]));
    expect(api).not.toHaveBeenCalled();

    const state = await seriesReading("tower");
    expect(state.signedIn).toBe(false);
    expect(state.following).toBe(false);
  });

  it("sends a signed-in reader's progress at most every interval, and the last position on leaving", async () => {
    vi.useFakeTimers();
    signedIn = true;
    api.mockResolvedValue(undefined);
    const progress = chapterProgress("tower", "3", meta);

    progress.update(0.1);
    await vi.advanceTimersByTimeAsync(0);
    expect(api).toHaveBeenCalledTimes(1);
    progress.update(0.2);
    progress.update(0.3);
    await vi.advanceTimersByTimeAsync(SYNC_INTERVAL_MS - 1000);
    expect(api).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(1000);
    expect(api).toHaveBeenCalledTimes(2);
    expect(api).toHaveBeenLastCalledWith(
      "PUT",
      "/api/v1/me/progress/tower",
      { number: "3", position: 0.3, finished: false },
      { keepalive: false },
    );

    progress.update(0.5);
    progress.leave(true);
    expect(api).toHaveBeenLastCalledWith(
      "PUT",
      "/api/v1/me/progress/tower",
      { number: "3", position: 0.5, finished: false },
      { keepalive: true },
    );
    expect(guest.getProgress("tower")).toBeUndefined();
  });

  it("imports the guest history once and then forgets it", async () => {
    guest.saveProgress("tower", "3", 0.5, new Date("2026-10-01T00:00:00Z"), meta);
    guest.markRead("tower", "2");
    guest.saveProgress("Not A Slug", "1", 0, new Date("2026-10-01T00:00:00Z"), meta);
    api.mockResolvedValue({ imported: 1, skipped: 0 });

    await importGuestHistory();

    expect(api).toHaveBeenCalledWith("POST", "/api/v1/me/history/import", {
      series: [
        {
          slug: "tower",
          number: "3",
          position: 0.5,
          updatedAt: "2026-10-01T00:00:00.000Z",
          read: ["2"],
        },
      ],
    });
    expect(guest.allHistory()).toEqual([]);
  });

  it("keeps the guest history when the import fails", async () => {
    guest.saveProgress("tower", "3", 0.5, new Date(), meta);
    api.mockRejectedValue(new Error("offline"));

    await importGuestHistory();

    expect(guest.getProgress("tower")).toBeDefined();
  });
});
