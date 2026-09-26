"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { type ReactNode, useEffect, useState } from "react";
import { chapterPath, seriesPath } from "@/lib/routes";

interface ReaderBarProps {
  slug: string;
  seriesTitle: string;
  number: string;
  chapters: { number: string; title?: string | null }[];
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

  useEffect(() => {
    let lastY = window.scrollY;
    function onScroll() {
      const y = window.scrollY;
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
              value={number}
              onChange={(event) => router.push(chapterPath(slug, event.target.value))}
              className="h-9 max-w-40 rounded border border-line bg-surface px-2 text-sm"
            >
              {chapters.map((chapter) => (
                <option key={chapter.number} value={chapter.number}>
                  Ch. {chapter.number}
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
