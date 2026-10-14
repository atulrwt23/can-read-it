import type { components } from "@/lib/api/schema";
import { ApiError, api, hasSignedInHint, setSignedInHint } from "@/lib/auth/client";
import * as guest from "@/lib/history";

type Schemas = components["schemas"];
export type LibraryPage = Schemas["LibraryPage"];
export type LibraryItem = Schemas["LibraryItem"];
export type HistoryPage = Schemas["HistoryPage"];
export type ReadingProgress = Schemas["ReadingProgress"];

/**
 * Where reading state lives: in the account when this browser is signed in, in browser storage for guests
 * (CLAUDE.md section 8). Every function picks the right place, so components don't have to. If the session turns
 * out to be gone, writes fall back to browser storage, which is imported at the next sign-in.
 */

export interface SeriesReading {
  /** Whether the state came from the account. */
  signedIn: boolean;
  following: boolean;
  /** The chapter last opened and how far through it, 0–1. */
  progress?: { number: string; position: number };
  /** Chapters read to the end. */
  read: Set<string>;
}

/** A recently read series, for "Continue reading". */
export interface RecentEntry {
  slug: string;
  number: string;
  position: number;
  series: guest.SeriesMeta;
}

const me = "/api/v1/me";

/** The guest history import in progress, if any: reads of the account wait for it. */
let importing: Promise<void> | null = null;
const path = (slug: string) => encodeURIComponent(slug);

/** True for the error the API answers when the session has ended for good (refreshing failed). */
function sessionEnded(error: unknown): boolean {
  if (error instanceof ApiError && error.status === 401) {
    setSignedInHint(false);
    return true;
  }
  return false;
}

// Several components on the series page ask at once; they share one request.
const seriesRequests = new Map<string, Promise<SeriesReading>>();

export function seriesReading(slug: string): Promise<SeriesReading> {
  const pending = seriesRequests.get(slug);
  if (pending) return pending;
  const request = loadSeriesReading(slug).finally(() => {
    setTimeout(() => seriesRequests.delete(slug), 1000);
  });
  seriesRequests.set(slug, request);
  return request;
}

async function loadSeriesReading(slug: string): Promise<SeriesReading> {
  if (hasSignedInHint()) {
    await importing;
    try {
      const state = await api<Schemas["SeriesReading"]>("GET", `${me}/series/${path(slug)}`);
      return {
        signedIn: true,
        following: state.following,
        progress: state.progress
          ? { number: state.progress.number, position: state.progress.position }
          : undefined,
        read: new Set(state.readChapters),
      };
    } catch (error) {
      if (!sessionEnded(error)) throw error;
    }
  }
  const progress = guest.getProgress(slug);
  return {
    signedIn: false,
    following: false,
    progress: progress ? { number: progress.number, position: progress.position } : undefined,
    read: guest.readChapters(slug),
  };
}

export async function setFollowing(slug: string, following: boolean): Promise<void> {
  seriesRequests.delete(slug);
  await api(following ? "PUT" : "DELETE", `${me}/library/${path(slug)}`);
}

export async function library(cursor?: string, limit = 24): Promise<LibraryPage> {
  await importing;
  const query = new URLSearchParams({ limit: String(limit) });
  if (cursor) query.set("cursor", cursor);
  return api<LibraryPage>("GET", `${me}/library?${query}`);
}

/** Recently read series, newest first. */
export async function recentlyRead(limit: number): Promise<RecentEntry[]> {
  if (hasSignedInHint()) {
    await importing;
    try {
      const page = await api<HistoryPage>("GET", `${me}/history?limit=${limit}`);
      return page.items.map((item) => ({
        slug: item.series.slug,
        number: item.progress.number,
        position: item.progress.position,
        series: { title: item.series.title, type: item.series.type, cover: item.series.cover },
      }));
    } catch (error) {
      if (!sessionEnded(error)) throw error;
    }
  }
  return guest
    .recentSeries(limit * 2)
    .filter((entry): entry is guest.RecentSeries & { series: guest.SeriesMeta } => !!entry.series)
    .slice(0, limit)
    .map((entry) => ({
      slug: entry.slug,
      number: entry.number,
      position: entry.position,
      series: entry.series,
    }));
}

/** Account progress is sent at most this often while reading; the last position is sent on leaving. */
export const SYNC_INTERVAL_MS = 10_000;

/**
 * Keeps one open chapter's progress: in browser storage for guests (cheap, so on every change), in the account
 * for signed-in readers (throttled, and flushed when the reader leaves).
 */
export function chapterProgress(slug: string, number: string, series: guest.SeriesMeta) {
  let account = hasSignedInHint();
  let pending: number | undefined;
  let timer: ReturnType<typeof setTimeout> | undefined;
  let lastSent = 0;

  function saveLocally(position: number, finished: boolean) {
    guest.saveProgress(slug, number, position, new Date(), series);
    if (finished) guest.markRead(slug, number);
  }

  async function send(position: number, finished: boolean, keepalive = false) {
    seriesRequests.delete(slug);
    lastSent = Date.now();
    try {
      await api(
        "PUT",
        `${me}/progress/${path(slug)}`,
        { number, position: Math.min(1, Math.max(0, position)), finished },
        { keepalive },
      );
    } catch (error) {
      // Signed out meanwhile: keep it here, so the next sign-in imports it.
      if (sessionEnded(error)) {
        account = false;
        saveLocally(position, finished);
      }
    }
  }

  function flush(keepalive = false) {
    clearTimeout(timer);
    timer = undefined;
    if (pending === undefined) return;
    const position = pending;
    pending = undefined;
    void send(position, false, keepalive);
  }

  return {
    /** Where to scroll to on opening: the saved position, if this chapter is the one in progress. */
    async restore(): Promise<number | undefined> {
      const state = await seriesReading(slug).catch(() => undefined);
      account = state?.signedIn ?? account;
      return state?.progress?.number === number ? state.progress.position : undefined;
    },
    update(position: number) {
      if (!account) {
        saveLocally(position, false);
        return;
      }
      pending = position;
      if (timer) return;
      const wait = Math.max(0, SYNC_INTERVAL_MS - (Date.now() - lastSent));
      timer = setTimeout(() => flush(), wait);
    },
    finish() {
      if (!account) {
        saveLocally(1, true);
        return;
      }
      clearTimeout(timer);
      timer = undefined;
      pending = undefined;
      void send(1, true);
    },
    /** Sends what is left; `keepalive` when the page is closing. */
    leave(keepalive = false) {
      if (account) flush(keepalive);
    },
  };
}

const SLUG = /^[a-z0-9]+(-[a-z0-9]+)*$/;
const CHAPTER = /^\d{1,6}(\.\d{1,2})?$/;

/**
 * Moves the guest history into the account, once, after sign-in. On success the browser copy is cleared; on
 * failure it stays and the next page load tries again.
 */
export function importGuestHistory(): Promise<void> {
  importing ??= (async () => {
    // Only what the API accepts, so one odd entry can't block the rest forever.
    const entries = guest
      .allHistory()
      .filter((entry) => SLUG.test(entry.slug) && CHAPTER.test(entry.number));
    if (entries.length === 0) {
      guest.clearHistory();
      return;
    }
    await api("POST", `${me}/history/import`, {
      series: entries.map((entry) => ({
        slug: entry.slug,
        number: entry.number,
        position: entry.position,
        updatedAt: entry.updatedAt,
        read: entry.read?.filter((number) => CHAPTER.test(number)).slice(-2000),
      })),
    });
    guest.clearHistory();
    seriesRequests.clear();
  })()
    .catch(() => {
      // Kept for the next try.
    })
    .finally(() => {
      importing = null;
    });
  return importing;
}
