"use client";

import Link from "next/link";
import { useState } from "react";
import { Badge } from "@/components/badge";
import { browserApi } from "@/lib/api/browser";
import type { ChapterPage, ChapterSummary } from "@/lib/api/types";
import { calendarDate, isNew } from "@/lib/format";
import { chapterPath } from "@/lib/routes";

type Order = "DESC" | "ASC";

interface ChapterListProps {
  slug: string;
  initial: ChapterPage;
  /** Render time from the server, so NEW badges match between server and client render. */
  now: string;
}

export function ChapterList({ slug, initial, now }: ChapterListProps) {
  const [order, setOrder] = useState<Order>("DESC");
  const [items, setItems] = useState<ChapterSummary[]>(initial.items);
  const [cursor, setCursor] = useState(initial.nextCursor);
  const [loading, setLoading] = useState(false);

  async function fetchPage(nextOrder: Order, nextCursor?: string) {
    setLoading(true);
    const { data } = await browserApi.GET("/api/v1/series/{slug}/chapters", {
      params: { path: { slug }, query: { order: nextOrder, cursor: nextCursor, limit: 100 } },
    });
    setLoading(false);
    return data;
  }

  async function toggleOrder() {
    const nextOrder: Order = order === "DESC" ? "ASC" : "DESC";
    const data = await fetchPage(nextOrder);
    if (data) {
      setOrder(nextOrder);
      setItems(data.items);
      setCursor(data.nextCursor);
    }
  }

  async function loadMore() {
    if (!cursor) return;
    const data = await fetchPage(order, cursor);
    if (data) {
      setItems((current) => [...current, ...data.items]);
      setCursor(data.nextCursor);
    }
  }

  const reference = new Date(now);

  return (
    <section aria-labelledby="chapters-title" className="rounded-md bg-surface p-4 shadow-card">
      <div className="mb-3 flex items-center justify-between">
        <h2 id="chapters-title" className="text-xl">
          Chapters
        </h2>
        <button
          type="button"
          onClick={toggleOrder}
          disabled={loading}
          className="rounded border border-line px-3 py-1 text-sm font-medium hover:bg-raised disabled:opacity-60"
        >
          {order === "DESC" ? "Newest first" : "Oldest first"}
        </button>
      </div>
      {items.length === 0 ? (
        <p className="text-muted">No chapters have been released yet.</p>
      ) : (
        <ul className="divide-y divide-line" aria-busy={loading}>
          {items.map((chapter) => (
            <li key={chapter.number}>
              <Link
                href={chapterPath(slug, chapter.number)}
                className="flex items-center gap-3 rounded px-2 py-2.5 hover:bg-raised"
              >
                <span className="w-20 shrink-0 font-semibold whitespace-nowrap">
                  Ch. {chapter.number}
                </span>
                <span className="min-w-0 flex-1 truncate text-sm text-muted">{chapter.title}</span>
                {chapter.access === "EARLY_ACCESS" && (
                  <span className="text-star" title="Early access">
                    <svg
                      aria-label="Early access"
                      role="img"
                      viewBox="0 0 24 24"
                      className="size-4"
                      fill="currentColor"
                    >
                      <path d="M12 2a5 5 0 0 0-5 5v3H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8a2 2 0 0 0-2-2h-1V7a5 5 0 0 0-5-5Zm-3 8V7a3 3 0 0 1 6 0v3H9Z" />
                    </svg>
                  </span>
                )}
                {isNew(chapter.publishedAt, reference) && <Badge tone="link">NEW</Badge>}
                <time dateTime={chapter.publishedAt} className="shrink-0 text-xs text-muted">
                  {calendarDate(chapter.publishedAt)}
                </time>
              </Link>
            </li>
          ))}
        </ul>
      )}
      {cursor && (
        <div className="mt-3 text-center">
          <button
            type="button"
            onClick={loadMore}
            disabled={loading}
            className="rounded border border-line px-4 py-2 text-sm font-semibold hover:bg-raised disabled:opacity-60"
          >
            {loading ? "Loading…" : "Load more chapters"}
          </button>
        </div>
      )}
    </section>
  );
}
