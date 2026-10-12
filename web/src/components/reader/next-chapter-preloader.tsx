"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { browserApi } from "@/lib/api/browser";
import { chapterPath } from "@/lib/routes";

/** How far into the chapter (0–1) the next one starts loading. */
const PRELOAD_AT = 0.6;
/** Next-chapter pages fetched ahead, so the first screen of it is ready. */
const PAGES_AHEAD = 3;

/**
 * Once the reader is well into a chapter, loads the next one ahead of time: the page itself (router prefetch) and,
 * for manhwa, its first few images. Runs once per chapter.
 */
export function NextChapterPreloader({
  slug,
  next,
  manga,
}: {
  slug: string;
  next: string | null | undefined;
  manga: boolean;
}) {
  const router = useRouter();

  useEffect(() => {
    if (!next) return;
    let done = false;
    async function preload(nextNumber: string) {
      router.prefetch(chapterPath(slug, nextNumber));
      if (!manga) return;
      const { data } = await browserApi
        .GET("/api/v1/series/{slug}/chapters/{number}", {
          params: { path: { slug, number: nextNumber } },
        })
        .catch(() => ({ data: undefined }));
      for (const page of data?.pages.slice(0, PAGES_AHEAD) ?? []) {
        const image = new Image();
        image.decoding = "async";
        image.src = page.url;
      }
    }
    function onScroll() {
      if (done || !next) return;
      const scrollable = document.documentElement.scrollHeight - window.innerHeight;
      if (scrollable <= 0 || window.scrollY / scrollable >= PRELOAD_AT) {
        done = true;
        window.removeEventListener("scroll", onScroll);
        void preload(next);
      }
    }
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, [router, slug, next, manga]);

  return null;
}
