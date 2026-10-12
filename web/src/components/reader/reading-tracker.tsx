"use client";

import { useEffect } from "react";
import { getProgress, markRead, type SeriesMeta, saveProgress } from "@/lib/history";

/** The element at the end of a chapter; reaching it marks the chapter read. */
export const CHAPTER_END_ID = "chapter-end";

/**
 * Remembers where a guest is in this chapter and returns them there next time. Page images carry
 * their sizes, so the document height is right before they load and restoring is exact. Reaching
 * the end of the chapter marks it read, unless `canFinish` is false (a locked chapter).
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
    const meta: SeriesMeta = {
      title,
      type,
      cover:
        coverUrl && coverWidth && coverHeight
          ? { url: coverUrl, width: coverWidth, height: coverHeight }
          : null,
    };
    const maxScroll = () => Math.max(1, document.documentElement.scrollHeight - window.innerHeight);
    const saved = getProgress(slug);
    if (saved?.number === number && saved.position > 0.02 && saved.position < 0.98) {
      window.scrollTo({ top: saved.position * maxScroll() });
    } else {
      saveProgress(slug, number, 0, new Date(), meta);
    }

    let timer: ReturnType<typeof setTimeout> | undefined;
    const save = () => saveProgress(slug, number, window.scrollY / maxScroll(), new Date(), meta);
    function onScroll() {
      if (!timer) {
        timer = setTimeout(() => {
          timer = undefined;
          save();
        }, 1000);
      }
    }
    window.addEventListener("scroll", onScroll, { passive: true });
    window.addEventListener("pagehide", save);

    const end = document.getElementById(CHAPTER_END_ID);
    const finished = new IntersectionObserver((entries) => {
      if (canFinish && entries.some((entry) => entry.isIntersecting)) {
        markRead(slug, number);
        finished.disconnect();
      }
    });
    if (end) finished.observe(end);

    return () => {
      finished.disconnect();
      clearTimeout(timer);
      save();
      window.removeEventListener("scroll", onScroll);
      window.removeEventListener("pagehide", save);
    };
  }, [slug, number, canFinish, title, type, coverUrl, coverWidth, coverHeight]);

  return null;
}
