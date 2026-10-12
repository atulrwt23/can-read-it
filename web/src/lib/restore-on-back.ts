/**
 * Keeps client-loaded state (such as "Load more" results) for a page in sessionStorage, and gives it back only
 * when the reader returns to that page with the browser's back or forward buttons. A fresh visit to the same URL
 * starts over. Storage may be unavailable: every access is guarded, and pages work without it.
 */
const PREFIX = "canreadit:restore:";
const MAX_AGE_MS = 30 * 60 * 1000;
/** A soft back navigation renders the page right after popstate; anything later is a new visit. */
const POPSTATE_WINDOW_MS = 3000;

let lastPopstate: { url: string; at: number } | undefined;

if (typeof window !== "undefined") {
  window.addEventListener("popstate", () => {
    lastPopstate = { url: currentUrl(), at: Date.now() };
  });
}

function currentUrl() {
  return window.location.pathname + window.location.search;
}

/** Whether this render of `url` comes from the back or forward button. */
export function isBackNavigation(url: string, now = Date.now()): boolean {
  return lastPopstate?.url === url && now - lastPopstate.at < POPSTATE_WINDOW_MS;
}

/** For tests: pretend a back navigation to `url` just happened. */
export function notePopstate(url: string, at = Date.now()) {
  lastPopstate = { url, at };
}

export function saveForBack<T>(url: string, state: T, now = Date.now()) {
  try {
    sessionStorage.setItem(PREFIX + url, JSON.stringify({ savedAt: now, state }));
  } catch {
    // Quota exceeded or storage disabled: going back simply starts over.
  }
}

export function restoreOnBack<T>(url: string, now = Date.now()): T | undefined {
  if (!isBackNavigation(url, now)) return undefined;
  try {
    const raw = sessionStorage.getItem(PREFIX + url);
    if (!raw) return undefined;
    const saved = JSON.parse(raw) as { savedAt?: number; state?: T };
    if (typeof saved.savedAt !== "number" || now - saved.savedAt > MAX_AGE_MS) return undefined;
    return saved.state;
  } catch {
    return undefined;
  }
}
