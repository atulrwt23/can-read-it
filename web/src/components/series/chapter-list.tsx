"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useId, useState } from "react";
import { Badge } from "@/components/badge";
import { browserApi } from "@/lib/api/browser";
import type { ChapterPage, ChapterSummary } from "@/lib/api/types";
import { calendarDate, isNew } from "@/lib/format";
import { useSeriesReading } from "@/lib/reading/use-series-reading";
import { chapterPath } from "@/lib/routes";

/** What a reader may type to jump to a chapter: "12", "12.5", "012.50". */
export const CHAPTER_NUMBER = /^\d{1,6}(\.\d{1,2})?$/;

type Order = "DESC" | "ASC";

const NONE: ReadonlySet<string> = new Set();

interface ChapterListProps {
  slug: string;
  initial: ChapterPage;
  /** Render time from the server, so NEW badges match between server and client render. */
  now: string;
}

export function ChapterList({ slug, initial, now }: ChapterListProps) {
  const [order, setOrder] = useState<Order>("DESC");
  // The reader's history (account or browser), known after hydration: chapters read to the end, the last opened.
  const [reading] = useSeriesReading(slug);
  const read = reading?.read ?? NONE;
  const lastRead = reading?.progress?.number;
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
      <div className="mb-3 flex flex-wrap items-center gap-2">
        <h2 id="chapters-title" className="mr-auto text-xl">
          Chapters
        </h2>
        <JumpToChapter slug={slug} />
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
          {items.map((chapter) => {
            const isRead = read.has(chapter.number);
            const isLast = chapter.number === lastRead;
            return (
              <li key={chapter.number}>
                <Link
                  href={chapterPath(slug, chapter.number)}
                  aria-current={isLast ? "location" : undefined}
                  className={`flex items-center gap-3 rounded px-2 py-2.5 hover:bg-raised ${isLast ? "bg-raised" : ""}`}
                >
                  <span
                    className={`w-20 shrink-0 font-semibold whitespace-nowrap ${isRead && !isLast ? "text-muted" : ""}`}
                  >
                    Ch. {chapter.number}
                  </span>
                  <span className="min-w-0 flex-1 truncate text-sm text-muted">
                    {chapter.title}
                  </span>
                  {isLast ? (
                    <Badge tone="primary">Last read</Badge>
                  ) : (
                    isRead && (
                      <span className="text-success" title="Read">
                        <svg
                          aria-label="Read"
                          role="img"
                          viewBox="0 0 24 24"
                          className="size-4"
                          fill="none"
                          stroke="currentColor"
                          strokeWidth="2.5"
                        >
                          <path d="m5 12 5 5L20 7" />
                        </svg>
                      </span>
                    )
                  )}
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
            );
          })}
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

/** "Go to chapter": opens a chapter by number, or says it isn't out instead of opening a "not found" page. */
function JumpToChapter({ slug }: { slug: string }) {
  const router = useRouter();
  const inputId = useId();
  const [error, setError] = useState<string | undefined>();
  const [checking, setChecking] = useState(false);

  async function jump(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const value = new FormData(event.currentTarget).get("number")?.toString().trim() ?? "";
    if (!CHAPTER_NUMBER.test(value)) {
      setError("Enter a chapter number, like 12 or 12.5.");
      return;
    }
    setChecking(true);
    setError(undefined);
    const result = await browserApi
      .GET("/api/v1/series/{slug}/chapters/{number}", { params: { path: { slug, number: value } } })
      .catch(() => undefined);
    setChecking(false);
    if (result?.response.status === 404) {
      setError(`Chapter ${value} isn't out yet.`);
      return;
    }
    // Found, or the check failed: the chapter page itself has the final word.
    router.push(chapterPath(slug, result?.data?.number ?? value));
  }

  return (
    <form onSubmit={jump} className="flex flex-col items-end gap-1" noValidate>
      <div className="flex gap-1">
        <label htmlFor={inputId} className="sr-only">
          Go to chapter
        </label>
        <input
          id={inputId}
          name="number"
          inputMode="decimal"
          autoComplete="off"
          placeholder="Ch. #"
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? `${inputId}-error` : undefined}
          className="h-8 w-20 rounded border border-line bg-surface px-2 text-sm placeholder:text-muted focus:border-primary-text focus:outline-none"
        />
        <button
          type="submit"
          disabled={checking}
          className="h-8 rounded border border-line px-3 text-sm font-medium hover:bg-raised disabled:opacity-60"
        >
          Go
        </button>
      </div>
      {error && (
        <p id={`${inputId}-error`} role="alert" className="text-xs text-muted">
          {error}
        </p>
      )}
    </form>
  );
}
