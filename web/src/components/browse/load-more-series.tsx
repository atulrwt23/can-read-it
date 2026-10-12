"use client";

import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { SeriesGridCard } from "@/components/series-grid-card";
import { browserApi } from "@/lib/api/browser";
import type { BrowseParams } from "@/lib/api/server";
import type { SeriesCard } from "@/lib/api/types";
import { restoreOnBack, saveForBack } from "@/lib/restore-on-back";

interface Saved {
  /** The first page's cursor: restored results only fit after the same first page. */
  cursor: string;
  items: SeriesCard[];
  next: string | null | undefined;
  scrollY: number;
}

/**
 * Appends further pages after the server-rendered first page, following the API cursor. Coming back to this page
 * with the browser's back button brings back the extra pages and the scroll position.
 */
export function LoadMoreSeries({ params, cursor }: { params: BrowseParams; cursor: string }) {
  const [items, setItems] = useState<SeriesCard[]>([]);
  const [next, setNext] = useState<string | null | undefined>(cursor);
  const [loading, setLoading] = useState(false);
  const [failed, setFailed] = useState(false);
  const [restoreScroll, setRestoreScroll] = useState<number | undefined>();
  const state = useRef<Saved>({ cursor, items: [], next: cursor, scrollY: 0 });

  useEffect(() => {
    const url = window.location.pathname + window.location.search;
    const saved = restoreOnBack<Saved>(url);
    if (saved?.cursor === cursor && saved.items.length > 0) {
      state.current = saved;
      setItems(saved.items);
      setNext(saved.next);
      setRestoreScroll(saved.scrollY);
    }
    const onScroll = () => {
      state.current.scrollY = window.scrollY;
    };
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => {
      window.removeEventListener("scroll", onScroll);
      // Leaving (usually to a series): remember what was loaded and where the reader was.
      if (state.current.items.length > 0) saveForBack(url, state.current);
    };
  }, [cursor]);

  // Scroll once the restored cards are in the page, so the position exists.
  useLayoutEffect(() => {
    if (restoreScroll !== undefined) {
      window.scrollTo({ top: restoreScroll });
      setRestoreScroll(undefined);
    }
  }, [restoreScroll]);

  async function load() {
    if (!next) return;
    setLoading(true);
    setFailed(false);
    const { data } = await browserApi
      .GET("/api/v1/series", { params: { query: { ...params, cursor: next } } })
      .catch(() => ({ data: undefined }));
    setLoading(false);
    if (!data) {
      setFailed(true);
      return;
    }
    const loaded = [...state.current.items, ...data.items];
    state.current = { ...state.current, items: loaded, next: data.nextCursor };
    setItems(loaded);
    setNext(data.nextCursor);
  }

  return (
    <>
      {items.map((series) => (
        <SeriesGridCard key={series.id} series={series} />
      ))}
      {next && (
        <div className="col-span-full flex flex-col items-center gap-2 pt-2">
          {failed && (
            <p role="alert" className="text-sm text-muted">
              Could not load more. Try again.
            </p>
          )}
          <button
            type="button"
            onClick={load}
            disabled={loading}
            className="rounded border border-line bg-surface px-4 py-2 text-sm font-semibold hover:bg-raised disabled:opacity-60"
          >
            {loading ? "Loading…" : "Load more"}
          </button>
        </div>
      )}
    </>
  );
}
