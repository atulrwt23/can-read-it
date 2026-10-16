import type { components } from "@/lib/api/schema";

type ClientEvent = components["schemas"]["ClientEvent"];

/** An event the browser may send; sign-ups are counted by the API itself. */
export type AnalyticsEvent = Omit<ClientEvent, "name"> & {
  name: Exclude<ClientEvent["name"], "SIGN_UP">;
};

const ENDPOINT = "/api/v1/analytics/events";
/** The API takes at most this many events per request. */
export const MAX_BATCH = 20;
/** Events wait this long so several go in one request; leaving the page sends them at once. */
export const FLUSH_MS = 5000;

let queue: AnalyticsEvent[] = [];
let timer: ReturnType<typeof setTimeout> | undefined;
let listening = false;

/**
 * Whether this browser may send events. Nothing personal is sent, but a visitor who asked not to be tracked (Do Not
 * Track or Global Privacy Control) sends nothing at all.
 */
export function trackingAllowed(): boolean {
  if (typeof window === "undefined" || typeof navigator === "undefined") return false;
  const nav = navigator as Navigator & { globalPrivacyControl?: boolean };
  return nav.doNotTrack !== "1" && nav.globalPrivacyControl !== true;
}

/** Queues an anonymous event (docs/modules/analytics.md). Never throws, never blocks. */
export function track(event: AnalyticsEvent) {
  if (!trackingAllowed()) return;
  queue.push(event);
  listen();
  if (queue.length >= MAX_BATCH) {
    flush();
  } else {
    timer ??= setTimeout(flush, FLUSH_MS);
  }
}

/** Sends what is queued, with `sendBeacon` so it survives the page closing. */
export function flush() {
  clearTimeout(timer);
  timer = undefined;
  while (queue.length > 0) {
    const events = queue.slice(0, MAX_BATCH);
    queue = queue.slice(MAX_BATCH);
    const body = JSON.stringify({ events });
    try {
      const sent = navigator.sendBeacon?.(ENDPOINT, new Blob([body], { type: "application/json" }));
      if (!sent) {
        void fetch(ENDPOINT, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body,
          keepalive: true,
          credentials: "omit",
        }).catch(() => undefined);
      }
    } catch {
      // Analytics never breaks the page.
    }
  }
}

function listen() {
  if (listening) return;
  listening = true;
  window.addEventListener("pagehide", flush);
  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "hidden") flush();
  });
}

/**
 * The route template of a path, so page views never carry anything a reader typed or a URL that names them.
 * Staff pages are not counted (`null`).
 */
export function routeTemplate(pathname: string): string | null {
  const path = pathname.replace(/\/+$/, "") || "/";
  if (path === "/admin" || path.startsWith("/admin/")) return null;
  if (/^\/series\/[^/]+\/chapter\/[^/]+$/.test(path)) return "/series/[slug]/chapter/[number]";
  if (/^\/series\/[^/]+$/.test(path)) return "/series/[slug]";
  const known = ["/", "/browse", "/library", "/login", "/login/reset", "/signup", "/account"];
  return known.includes(path) ? path : "/[other]";
}

/** For tests: forget queued events. */
export function resetForTests() {
  clearTimeout(timer);
  timer = undefined;
  queue = [];
}
