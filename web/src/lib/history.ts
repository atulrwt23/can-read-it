/**
 * Guest reading history in localStorage (CLAUDE.md section 8). After sign-in (step 4) it is sent
 * once to POST /api/v1/me/history/import and cleared. Storage may be unavailable: every access
 * is guarded, and the app works without it.
 */
const KEY = "canreadit:history:v1";
const MAX_SERIES = 200;

export interface Progress {
  /** Chapter number as the API formats it, e.g. "12.5". */
  number: string;
  /** Scroll position within the chapter, 0–1. */
  position: number;
  updatedAt: string;
}

type History = Record<string, Progress>;

function read(): History {
  try {
    const raw = localStorage.getItem(KEY);
    const parsed: unknown = raw ? JSON.parse(raw) : {};
    return parsed && typeof parsed === "object" ? (parsed as History) : {};
  } catch {
    return {};
  }
}

export function getProgress(seriesSlug: string): Progress | undefined {
  return read()[seriesSlug];
}

export function saveProgress(
  seriesSlug: string,
  number: string,
  position: number,
  now = new Date(),
) {
  const history = read();
  history[seriesSlug] = {
    number,
    position: Math.min(1, Math.max(0, position)),
    updatedAt: now.toISOString(),
  };
  // Keep the most recently read series only.
  const kept = Object.entries(history)
    .sort(([, a], [, b]) => b.updatedAt.localeCompare(a.updatedAt))
    .slice(0, MAX_SERIES);
  try {
    localStorage.setItem(KEY, JSON.stringify(Object.fromEntries(kept)));
  } catch {
    // Quota exceeded or storage disabled: progress is simply not remembered.
  }
}
