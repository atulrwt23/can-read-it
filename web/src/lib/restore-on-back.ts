/**
 * Keeps client-loaded state (such as "Load more" results) for a page in sessionStorage, and gives it back only
 * when the reader returns to that page with the browser's back or forward buttons. A fresh visit to the same URL
 * starts over. Storage may be unavailable: every access is guarded, and pages work without it.
 *
 * With the Navigation API, state belongs to the history entry: going back returns to the same entry (same key),
 * while a new visit creates a new one. Without it, a popstate just before the render marks a back navigation.
 */
const PREFIX = "canreadit:restore:";
const MAX_AGE_MS = 30 * 60 * 1000;
/** A back navigation renders the page right after popstate; anything later is a new visit. */
const POPSTATE_WINDOW_MS = 3000;

let lastPopstate: { url: string; at: number } | undefined;

if (typeof window !== "undefined") {
  window.addEventListener(
    "popstate",
    () => {
      lastPopstate = { url: window.location.pathname + window.location.search, at: Date.now() };
    },
    { capture: true },
  );
}

/** Where a page's state is kept. Take it when the page mounts: by unmount the browser is on the next entry. */
export interface BackKey {
  url: string;
  /** The history entry's key, when the browser has the Navigation API. */
  entry?: string;
}

interface NavigationLike {
  currentEntry?: { key?: string } | null;
}

export function backKey(url: string): BackKey {
  const navigation = (globalThis as { navigation?: NavigationLike }).navigation;
  const entry = navigation?.currentEntry?.key;
  return entry ? { url, entry } : { url };
}

/** For tests: pretend a popstate to `url` just happened. */
export function notePopstate(url: string, at = Date.now()) {
  lastPopstate = { url, at };
}

function storageKey(key: BackKey) {
  return PREFIX + (key.entry ? `${key.entry}:` : "") + key.url;
}

export function saveForBack<T>(key: BackKey, state: T, now = Date.now()) {
  try {
    sessionStorage.setItem(storageKey(key), JSON.stringify({ savedAt: now, state }));
  } catch {
    // Quota exceeded or storage disabled: going back simply starts over.
  }
}

export function restoreOnBack<T>(key: BackKey, now = Date.now()): T | undefined {
  const cameBack =
    key.entry !== undefined ||
    (lastPopstate?.url === key.url && now - lastPopstate.at < POPSTATE_WINDOW_MS);
  if (!cameBack) return undefined;
  try {
    const raw = sessionStorage.getItem(storageKey(key));
    if (!raw) return undefined;
    const saved = JSON.parse(raw) as { savedAt?: number; state?: T };
    if (typeof saved.savedAt !== "number" || now - saved.savedAt > MAX_AGE_MS) return undefined;
    return saved.state;
  } catch {
    return undefined;
  }
}
