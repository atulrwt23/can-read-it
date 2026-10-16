import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { FLUSH_MS, flush, MAX_BATCH, resetForTests, routeTemplate, track } from "./analytics";

const sendBeacon = vi.fn((_url: string, _body: Blob) => true);
let navigatorState: Record<string, unknown> = {};

beforeEach(() => {
  vi.useFakeTimers();
  sendBeacon.mockClear();
  navigatorState = { sendBeacon, doNotTrack: null };
  vi.stubGlobal("navigator", navigatorState);
  vi.stubGlobal("window", { addEventListener: vi.fn() });
  vi.stubGlobal("document", { addEventListener: vi.fn(), visibilityState: "visible" });
  resetForTests();
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

async function sentEvents(call: number): Promise<unknown[]> {
  const blob = sendBeacon.mock.calls[call]?.[1] as Blob;
  return (JSON.parse(await blob.text()) as { events: unknown[] }).events;
}

describe("track", () => {
  it("batches events and sends them after a short wait", async () => {
    track({ name: "PAGE_VIEW", path: "/" });
    track({ name: "SERIES_VIEW", series: "tower" });
    expect(sendBeacon).not.toHaveBeenCalled();

    vi.advanceTimersByTime(FLUSH_MS);
    expect(sendBeacon).toHaveBeenCalledTimes(1);
    expect(sendBeacon.mock.calls[0]?.[0]).toBe("/api/v1/analytics/events");
    expect(await sentEvents(0)).toEqual([
      { name: "PAGE_VIEW", path: "/" },
      { name: "SERIES_VIEW", series: "tower" },
    ]);
  });

  it("sends a full batch at once and splits larger queues", () => {
    for (let i = 0; i < MAX_BATCH; i++) track({ name: "PAGE_VIEW", path: "/" });
    expect(sendBeacon).toHaveBeenCalledTimes(1);
    track({ name: "PAGE_VIEW", path: "/" });
    flush();
    expect(sendBeacon).toHaveBeenCalledTimes(2);
  });

  it("sends nothing when the visitor asked not to be tracked", () => {
    navigatorState.doNotTrack = "1";
    track({ name: "PAGE_VIEW", path: "/" });
    flush();
    navigatorState.doNotTrack = null;
    navigatorState.globalPrivacyControl = true;
    track({ name: "PAGE_VIEW", path: "/" });
    flush();
    expect(sendBeacon).not.toHaveBeenCalled();
  });
});

describe("routeTemplate", () => {
  it.each([
    ["/", "/"],
    ["/browse", "/browse"],
    ["/library/", "/library"],
    ["/series/tower-of-quiet-stars", "/series/[slug]"],
    ["/series/tower-of-quiet-stars/chapter/12.5", "/series/[slug]/chapter/[number]"],
    ["/some/unknown/page", "/[other]"],
  ])("turns %s into %s", (path, template) => {
    expect(routeTemplate(path)).toBe(template);
  });

  it("leaves staff pages out", () => {
    expect(routeTemplate("/admin")).toBeNull();
    expect(routeTemplate("/admin/analytics")).toBeNull();
  });
});
