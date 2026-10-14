/**
 * Guest reading history in localStorage (CLAUDE.md section 8). After sign-in it is sent once to
 * POST /api/v1/me/history/import and cleared (lib/reading/store.ts); signed-in reading lives in the
 * account. Storage may be unavailable: every access is guarded, and the app works without it.
 */
const KEY = "canreadit:history:v1";
const MAX_SERIES = 200;
/** Read marks kept per series; the oldest marks go first. */
const MAX_READ = 2000;

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
  /** Chapters read to the end, in the order they were finished. */
  read?: string[];
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
    read: history[seriesSlug]?.read,
  };
  write(history);
}

/** Chapters of a series read to the end. */
export function readChapters(seriesSlug: string): Set<string> {
  const marks = read()[seriesSlug]?.read;
  return new Set(Array.isArray(marks) ? marks.filter((n) => typeof n === "string") : []);
}

/** Marks a chapter read to the end. Only series with saved progress keep marks. */
export function markRead(seriesSlug: string, number: string) {
  const history = read();
  const entry = history[seriesSlug];
  if (!entry) return;
  const marks = Array.isArray(entry.read) ? entry.read.filter((n) => n !== number) : [];
  entry.read = [...marks, number].slice(-MAX_READ);
  write(history);
}

/** Every series in the guest history, for the import after sign-in. */
export function allHistory(): RecentSeries[] {
  return recentSeries(MAX_SERIES);
}

/** Forgets the guest history (it now lives in the account). */
export function clearHistory() {
  try {
    localStorage.removeItem(KEY);
  } catch {
    // Storage unavailable: nothing was kept.
  }
}

function write(history: History) {
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
