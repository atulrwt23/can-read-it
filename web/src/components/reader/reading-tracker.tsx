"use client";

import { useEffect } from "react";
import type { SeriesMeta } from "@/lib/history";
import { chapterProgress } from "@/lib/reading/store";

/** The element at the end of a chapter; reaching it marks the chapter read. */
export const CHAPTER_END_ID = "chapter-end";

/**
 * Remembers where the reader is in this chapter (in the account when signed in, in the browser for guests) and
 * returns them there next time. Page images carry their sizes, so the document height is right before they load
 * and restoring is exact. Reaching the end of the chapter marks it read, unless `canFinish` is false (a locked
 * chapter).
 */
export function ReadingTracker({
  slug,
  number,
  series,
  canFinish = true,
}: {
  slug: string;
  number: string;
  series: SeriesMeta;
  canFinish?: boolean;
}) {
  // Plain values, so the effect doesn't restart when the parent passes a new object.
  const { title, type } = series;
  const coverUrl = series.cover?.url;
  const coverWidth = series.cover?.width;
  const coverHeight = series.cover?.height;

  useEffect(() => {
    const progress = chapterProgress(slug, number, {
      title,
      type,
      cover:
        coverUrl && coverWidth && coverHeight
          ? { url: coverUrl, width: coverWidth, height: coverHeight }
          : null,
    });
    const maxScroll = () => Math.max(1, document.documentElement.scrollHeight - window.innerHeight);
    const position = () => window.scrollY / maxScroll();
    let active = true;
    let finished = false;

    void progress.restore().then((saved) => {
      if (!active) return;
      if (saved !== undefined && saved > 0.02 && saved < 0.98) {
        // Only jump if the reader hasn't started scrolling already.
        if (window.scrollY < 50) window.scrollTo({ top: saved * maxScroll() });
      } else {
        // Opening a chapter is progress too: it becomes the one to continue.
        progress.update(position());
      }
    });

    let timer: ReturnType<typeof setTimeout> | undefined;
    function onScroll() {
      if (!timer) {
        timer = setTimeout(() => {
          timer = undefined;
          if (!finished) progress.update(position());
        }, 1000);
      }
    }
    function onPageHide() {
      if (!finished) progress.update(position());
      progress.leave(true);
    }
    window.addEventListener("scroll", onScroll, { passive: true });
    window.addEventListener("pagehide", onPageHide);

    const end = document.getElementById(CHAPTER_END_ID);
    const observer = new IntersectionObserver((entries) => {
      if (canFinish && entries.some((entry) => entry.isIntersecting)) {
        finished = true;
        progress.finish();
        observer.disconnect();
      }
    });
    if (end) observer.observe(end);

    return () => {
      active = false;
      observer.disconnect();
      clearTimeout(timer);
      if (!finished) progress.update(position());
      progress.leave();
      window.removeEventListener("scroll", onScroll);
      window.removeEventListener("pagehide", onPageHide);
    };
  }, [slug, number, canFinish, title, type, coverUrl, coverWidth, coverHeight]);

  return null;
}
