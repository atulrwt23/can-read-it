import type { Metadata } from "next";
import Link from "next/link";

/** Metadata for a removed series: it should drop out of search results. */
export const removedMetadata: Metadata = {
  title: "Series removed",
  robots: { index: false, follow: true },
};

/**
 * Shown for a series that was taken down (the API answers 410). Next.js can only send 404 for a missing page, so
 * the page says what happened and asks search engines not to index it.
 */
export function SeriesRemoved() {
  return (
    <main className="mx-auto max-w-md px-4 py-16 text-center">
      <h1 className="mb-2 text-3xl">This series was removed</h1>
      <p className="mb-6 text-muted">
        It is no longer available on this site. Your reading history for it stays in this browser.
      </p>
      <div className="flex justify-center gap-3">
        <Link
          href="/"
          className="rounded border border-line px-4 py-2 text-sm font-semibold hover:bg-raised"
        >
          Home
        </Link>
        <Link
          href="/browse"
          className="rounded bg-primary px-4 py-2 text-sm font-semibold text-on-primary"
        >
          Browse series
        </Link>
      </div>
    </main>
  );
}
