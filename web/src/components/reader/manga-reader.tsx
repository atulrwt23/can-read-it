"use client";

import { useEffect, useRef, useState } from "react";
import type { ImageRef } from "@/lib/api/types";
import { useMangaSettings, WIDTH_CSS, WIDTHS } from "@/lib/manga-settings";

/** Wait before retrying a page that failed to load; a second failure shows a retry button. */
const AUTO_RETRY_MS = 2000;

/**
 * The continuous strip of manhwa pages, as wide as the reader's width setting. Every page keeps its size from the
 * API, so the layout never shifts, and a page that fails to load retries once by itself and then offers a button.
 */
export function MangaPages({ pages }: { pages: ImageRef[] }) {
  const [settings] = useMangaSettings();
  return (
    <div className="mx-auto bg-surface" style={{ maxWidth: WIDTH_CSS[settings.width] }}>
      {pages.map((page, index) => (
        <MangaPage key={page.url} page={page} index={index} />
      ))}
    </div>
  );
}

function MangaPage({ page, index }: { page: ImageRef; index: number }) {
  // Each attempt renders a new <img>, so the browser requests the image again.
  const [attempt, setAttempt] = useState(0);
  const [failed, setFailed] = useState(false);
  const image = useRef<HTMLImageElement>(null);

  // An image that failed before hydration fired its error event before React listened for it.
  useEffect(() => {
    const img = image.current;
    if (img?.complete && img.naturalWidth === 0) setFailed(true);
  }, []);

  useEffect(() => {
    if (!failed || attempt > 0) return;
    const timer = setTimeout(() => {
      setFailed(false);
      setAttempt(1);
    }, AUTO_RETRY_MS);
    return () => clearTimeout(timer);
  }, [failed, attempt]);

  if (failed && attempt > 0) {
    return (
      <div
        className="flex w-full flex-col items-center justify-center gap-3 bg-raised text-sm text-muted"
        style={{ aspectRatio: `${page.width} / ${page.height}` }}
      >
        <p>Page {index + 1} didn't load.</p>
        <button
          type="button"
          onClick={() => {
            setFailed(false);
            setAttempt((n) => n + 1);
          }}
          className="rounded border border-line bg-surface px-4 py-2 font-semibold text-text hover:bg-raised"
        >
          Try again
        </button>
      </div>
    );
  }

  return (
    <img
      key={attempt}
      ref={image}
      src={page.url}
      width={page.width}
      height={page.height}
      alt={`Page ${index + 1}`}
      loading={index < 2 ? "eager" : "lazy"}
      fetchPriority={index === 0 ? "high" : undefined}
      decoding="async"
      onError={() => setFailed(true)}
      className="block h-auto w-full"
    />
  );
}

const option =
  "rounded border border-line px-3 py-1.5 text-sm font-medium aria-pressed:border-primary aria-pressed:bg-primary aria-pressed:text-on-primary";

const WIDTH_LABELS: Record<(typeof WIDTHS)[number], string> = {
  narrow: "Narrow",
  standard: "Standard",
  wide: "Wide",
  full: "Full",
};

/** Button with a popover to choose how wide pages are on large screens. */
export function MangaSettingsButton() {
  const [settings, update] = useMangaSettings();
  return (
    <>
      <button
        type="button"
        popoverTarget="manga-settings"
        aria-label="Reading settings"
        className="inline-flex size-9 items-center justify-center rounded hover:bg-raised"
      >
        <svg
          aria-hidden="true"
          viewBox="0 0 24 24"
          className="size-5"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
        >
          <path d="M3 12h18M7 8l-4 4 4 4M17 8l4 4-4 4" />
        </svg>
      </button>
      <div
        id="manga-settings"
        popover="auto"
        role="dialog"
        aria-label="Reading settings"
        className="m-0 mt-14 ml-auto mr-2 w-72 rounded-md border border-line bg-surface p-4 text-text shadow-card"
      >
        <fieldset>
          <legend className="mb-2 text-sm text-muted">Page width on large screens</legend>
          <div className="flex flex-wrap gap-2">
            {WIDTHS.map((width) => (
              <button
                key={width}
                type="button"
                aria-pressed={settings.width === width}
                className={option}
                onClick={() => update({ width })}
              >
                {WIDTH_LABELS[width]}
              </button>
            ))}
          </div>
        </fieldset>
      </div>
    </>
  );
}
