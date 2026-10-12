"use client";

import { useEffect } from "react";
import { getProgress, type SeriesMeta, saveProgress } from "@/lib/history";

/**
 * Remembers where a guest is in this chapter and returns them there next time. Page images carry
 * their sizes, so the document height is right before they load and restoring is exact.
 */
export function ReadingTracker({
  slug,
  number,
  series,
}: {
  slug: string;
  number: string;
  series: SeriesMeta;
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
    return () => {
      clearTimeout(timer);
      save();
      window.removeEventListener("scroll", onScroll);
      window.removeEventListener("pagehide", save);
    };
  }, [slug, number, title, type, coverUrl, coverWidth, coverHeight]);

  return null;
}
