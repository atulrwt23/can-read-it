/**
 * Guest reading history in localStorage (CLAUDE.md section 8). After sign-in (step 4) it is sent
 * once to POST /api/v1/me/history/import and cleared. Storage may be unavailable: every access
 * is guarded, and the app works without it.
 */
const KEY = "canreadit:history:v1";
const MAX_SERIES = 200;

/** What "Continue reading" shows for a series, saved while reading so the home page needs no request. */
export interface SeriesMeta {
  title: string;
  type: "MANHWA" | "NOVEL";
  cover?: { url: string; width: number; height: number } | null;
}

export interface Progress {
  /** Chapter number as the API formats it, e.g. "12.5". */
  number: string;
  /** Scroll position within the chapter, 0–1. */
  position: number;
  updatedAt: string;
  /** Missing for progress saved before it was recorded; filled in the next time the series is read. */
  series?: SeriesMeta;
}

export interface RecentSeries extends Progress {
  slug: string;
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

/** The most recently read series first, at most `limit`. */
export function recentSeries(limit: number): RecentSeries[] {
  return Object.entries(read())
    .filter(([, progress]) => typeof progress?.updatedAt === "string")
    .sort(([, a], [, b]) => b.updatedAt.localeCompare(a.updatedAt))
    .slice(0, limit)
    .map(([slug, progress]) => ({ slug, ...progress }));
}

export function saveProgress(
  seriesSlug: string,
  number: string,
  position: number,
  now = new Date(),
  series?: SeriesMeta,
) {
  const history = read();
  history[seriesSlug] = {
    number,
    position: Math.min(1, Math.max(0, position)),
    updatedAt: now.toISOString(),
    series: series ?? history[seriesSlug]?.series,
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
