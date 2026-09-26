"use client";

import { useState } from "react";
import { SeriesGridCard } from "@/components/series-grid-card";
import { browserApi } from "@/lib/api/browser";
import type { BrowseParams } from "@/lib/api/server";
import type { SeriesCard } from "@/lib/api/types";

/** Appends further pages after the server-rendered first page, following the API cursor. */
export function LoadMoreSeries({ params, cursor }: { params: BrowseParams; cursor: string }) {
  const [items, setItems] = useState<SeriesCard[]>([]);
  const [next, setNext] = useState<string | null | undefined>(cursor);
  const [loading, setLoading] = useState(false);
  const [failed, setFailed] = useState(false);

  async function load() {
    if (!next) return;
    setLoading(true);
    setFailed(false);
    const { data } = await browserApi.GET("/api/v1/series", {
      params: { query: { ...params, cursor: next } },
    });
    setLoading(false);
    if (!data) {
      setFailed(true);
      return;
    }
    setItems((current) => [...current, ...data.items]);
    setNext(data.nextCursor);
  }

  return (
    <>
      {items.map((series) => (
        <SeriesGridCard key={series.id} series={series} />
      ))}
      {next && (
        <div className="col-span-full flex flex-col items-center gap-2 pt-2">
          {failed && <p className="text-sm text-muted">Could not load more. Try again.</p>}
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
