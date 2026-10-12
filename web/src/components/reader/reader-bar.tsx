"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { type ReactNode, useEffect, useRef, useState } from "react";
import { browserApi } from "@/lib/api/browser";
import type { ChapterPage } from "@/lib/api/types";
import { chapterPath, seriesPath } from "@/lib/routes";

interface ReaderBarProps {
  slug: string;
  seriesTitle: string;
  number: string;
  /** The first page of chapters, oldest first; the rest load in the background. */
  chapters: ChapterPage;
  previous?: string | null;
  next?: string | null;
  actions?: ReactNode;
}

const navButton =
  "inline-flex h-9 items-center justify-center rounded px-3 text-sm font-semibold hover:bg-raised aria-disabled:pointer-events-none aria-disabled:opacity-40";

/**
 * Top and bottom reader bars. They slide away while scrolling down and come back when scrolling
 * up, tapping the page, or nearing the end. Arrow keys move between chapters.
 */
export function ReaderBar({
  slug,
  seriesTitle,
  number,
  chapters,
  previous,
  next,
  actions,
}: ReaderBarProps) {
  const router = useRouter();
  const [visible, setVisible] = useState(true);
  const numbers = withChapter(useAllChapterNumbers(slug, chapters), number);
  const progress = useRef<HTMLDivElement>(null);

  useEffect(() => {
    let lastY = window.scrollY;
    function onScroll() {
      const y = window.scrollY;
      const scrollable = document.documentElement.scrollHeight - window.innerHeight;
      if (progress.current) {
        progress.current.style.transform = `scaleX(${scrollable > 0 ? Math.min(1, y / scrollable) : 1})`;
      }
      const nearEnd = window.innerHeight + y >= document.documentElement.scrollHeight - 200;
      if (nearEnd || y < 80 || y < lastY - 8) setVisible(true);
      else if (y > lastY + 8) setVisible(false);
      lastY = y;
    }
    function onClick(event: MouseEvent) {
      const target = event.target as HTMLElement;
      if (target.closest("a, button, select, input, [popover], [data-reader-bar]")) return;
      setVisible((v) => !v);
    }
    function onKey(event: KeyboardEvent) {
      const target = event.target as HTMLElement;
      if (
        target.closest("input, select, textarea") ||
        event.altKey ||
        event.ctrlKey ||
        event.metaKey
      )
        return;
      if (event.key === "ArrowLeft" && previous) router.push(chapterPath(slug, previous));
      if (event.key === "ArrowRight" && next) router.push(chapterPath(slug, next));
    }
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
    document.addEventListener("click", onClick);
    document.addEventListener("keydown", onKey);
    return () => {
      window.removeEventListener("scroll", onScroll);
      document.removeEventListener("click", onClick);
      document.removeEventListener("keydown", onKey);
    };
  }, [router, slug, previous, next]);

  const hidden = visible ? "" : "pointer-events-none";

  const prevNext = (
    <>
      <Link
        href={previous ? chapterPath(slug, previous) : "#"}
        aria-disabled={!previous}
        tabIndex={previous ? undefined : -1}
        className={navButton}
      >
        ‹ Prev
      </Link>
      <Link
        href={next ? chapterPath(slug, next) : "#"}
        aria-disabled={!next}
        tabIndex={next ? undefined : -1}
        className={navButton}
      >
        Next ›
      </Link>
    </>
  );

  return (
    <>
      {/* Reading progress, visible even while the bars are hidden. */}
      <div aria-hidden="true" className="fixed inset-x-0 top-0 z-30 h-0.5">
        <div
          ref={progress}
          className="h-full origin-left bg-primary"
          style={{ transform: "scaleX(0)" }}
        />
      </div>
      <header
        data-reader-bar
        className={`fixed inset-x-0 top-0 z-20 border-b border-line bg-surface/95 backdrop-blur transition-transform duration-200 ${visible ? "" : "-translate-y-full"} ${hidden}`}
      >
        <div className="mx-auto flex h-14 max-w-4xl items-center gap-2 px-3">
          <Link
            href={seriesPath(slug)}
            className="flex min-w-0 items-center gap-1 rounded px-2 py-1 hover:bg-raised"
          >
            <span aria-hidden="true">‹</span>
            <span className="truncate text-sm font-semibold">{seriesTitle}</span>
          </Link>
          <label className="ml-auto shrink-0">
            <span className="sr-only">Chapter</span>
            <select
              aria-label="Chapter"
              value={number}
              onChange={(event) => router.push(chapterPath(slug, event.target.value))}
              className="h-9 max-w-40 rounded border border-line bg-surface px-2 text-sm"
            >
              {numbers.map((chapter) => (
                <option key={chapter} value={chapter}>
                  Ch. {chapter}
                </option>
              ))}
            </select>
          </label>
          <div className="hidden items-center sm:flex">{prevNext}</div>
          {actions}
        </div>
      </header>
      <nav
        data-reader-bar
        aria-label="Chapter navigation"
        className={`fixed inset-x-0 bottom-0 z-20 border-t border-line bg-surface/95 backdrop-blur transition-transform duration-200 sm:hidden ${visible ? "" : "translate-y-full"} ${hidden}`}
      >
        <div className="flex h-12 items-center justify-between px-3">{prevNext}</div>
      </nav>
    </>
  );
}

/** Pages of the chapter list fetched in the background, so the picker offers every chapter however long. */
const PAGE_SIZE = 200;

/**
 * Every chapter number, oldest first. Starts with the server-rendered first page (plus the open chapter, in case
 * it is further along), then fetches the remaining pages once the browser is idle.
 */
function useAllChapterNumbers(slug: string, first: ChapterPage): string[] {
  const initial = first.items.map((chapter) => chapter.number);
  const [loaded, setLoaded] = useState<string[]>([]);

  useEffect(() => {
    if (!first.nextCursor) return;
    let cancelled = false;
    const start = async () => {
      let cursor: string | null | undefined = first.nextCursor;
      const more: string[] = [];
      while (cursor && !cancelled) {
        const { data } = await browserApi
          .GET("/api/v1/series/{slug}/chapters", {
            params: { path: { slug }, query: { order: "ASC", limit: PAGE_SIZE, cursor } },
          })
          .catch(() => ({ data: undefined }));
        if (!data) break;
        more.push(...data.items.map((chapter) => chapter.number));
        cursor = data.nextCursor;
      }
      if (!cancelled) setLoaded(more);
    };
    const idle = window.requestIdleCallback ?? ((fn: () => void) => window.setTimeout(fn, 500));
    const cancelIdle = window.cancelIdleCallback ?? window.clearTimeout;
    const handle = idle(() => void start());
    return () => {
      cancelled = true;
      cancelIdle(handle);
    };
  }, [slug, first.nextCursor]);

  return [...initial, ...loaded];
}

/** Makes sure the open chapter is in the list (the first page may not reach it yet), in number order. */
export function withChapter(numbers: string[], current: string): string[] {
  if (numbers.includes(current)) return numbers;
  return [...numbers, current].sort((a, b) => Number(a) - Number(b));
}
