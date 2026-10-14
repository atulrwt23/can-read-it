"use client";

import Link from "next/link";
import { type ReactNode, useCallback, useEffect, useState } from "react";
import { useSession } from "@/components/auth/session";
import { Cover } from "@/components/cover";
import { SeriesGridCard } from "@/components/series-grid-card";
import { CardGridSkeleton } from "@/components/skeleton";
import { errorMessage } from "@/lib/auth/client";
import { type LibraryItem, library, type RecentEntry, recentlyRead } from "@/lib/reading/store";
import { chapterPath } from "@/lib/routes";

const GRID = "grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6";

/**
 * The reader's library: followed series and reading history. Personal, so it renders in the browser. Guests see
 * the history kept in this browser and an invitation to sign in.
 */
export function LibraryView() {
  const session = useSession();
  return (
    <main className="mx-auto flex max-w-6xl flex-col gap-8 px-4 py-8">
      <h1 className="text-3xl">Library</h1>
      {session.status === "loading" ? (
        <>
          <p role="status" className="sr-only">
            Loading your library…
          </p>
          <CardGridSkeleton count={6} className={GRID} />
        </>
      ) : session.status === "signed-out" ? (
        <>
          <Section id="follow-title" title="Follow series">
            <div className="flex flex-col items-start gap-3 rounded-md bg-surface p-6 shadow-card">
              <p>Sign in to follow series and keep your place on every device.</p>
              <Link
                href="/login?next=/library"
                className="rounded bg-primary px-4 py-2 text-sm font-semibold text-on-primary hover:brightness-95"
              >
                Sign in
              </Link>
            </div>
          </Section>
          <History emptyText="Series you read appear here." />
        </>
      ) : (
        <>
          <Following />
          <History emptyText="Series you read appear here, on every device you sign in on." />
        </>
      )}
    </main>
  );
}

function Section({ id, title, children }: { id: string; title: string; children: ReactNode }) {
  return (
    <section aria-labelledby={id} className="flex flex-col gap-3">
      <h2 id={id} className="text-xl">
        {title}
      </h2>
      {children}
    </section>
  );
}

function Following() {
  const [items, setItems] = useState<LibraryItem[] | null>(null);
  const [cursor, setCursor] = useState<string | null | undefined>();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async (after?: string) => {
    setLoading(true);
    setError(null);
    try {
      const page = await library(after);
      setItems((current) => [...(after ? (current ?? []) : []), ...page.items]);
      setCursor(page.nextCursor);
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <Section id="following-title" title="Following">
      {error ? (
        <p role="alert" className="text-sm text-muted">
          {error}
        </p>
      ) : null}
      {items === null && !error ? (
        <>
          <p role="status" className="sr-only">
            Loading the series you follow…
          </p>
          <CardGridSkeleton count={6} className={GRID} />
        </>
      ) : null}
      {items && items.length === 0 ? (
        <p className="rounded-md bg-surface p-6 text-muted shadow-card">
          You don't follow any series yet. Open a series and choose <strong>Follow</strong>.{" "}
          <Link href="/browse" className="text-link underline">
            Browse series
          </Link>
        </p>
      ) : null}
      {items && items.length > 0 ? (
        <div className={GRID}>
          {items.map((item) => (
            <div key={item.series.id} className="flex flex-col gap-1">
              <SeriesGridCard series={item.series} />
              {item.progress ? (
                <Link
                  href={chapterPath(item.series.slug, item.progress.number)}
                  className="px-2 text-xs font-semibold text-primary-text hover:underline"
                >
                  Continue Ch. {item.progress.number}
                </Link>
              ) : null}
            </div>
          ))}
          {cursor ? (
            <div className="col-span-full flex justify-center pt-2">
              <button
                type="button"
                onClick={() => void load(cursor)}
                disabled={loading}
                className="rounded border border-line bg-surface px-4 py-2 text-sm font-semibold hover:bg-raised disabled:opacity-60"
              >
                {loading ? "Loading…" : "Load more"}
              </button>
            </div>
          ) : null}
        </div>
      ) : null}
    </Section>
  );
}

function History({ emptyText }: { emptyText: string }) {
  const [entries, setEntries] = useState<RecentEntry[] | null>(null);

  useEffect(() => {
    let current = true;
    recentlyRead(24)
      .then((recent) => current && setEntries(recent))
      .catch(() => current && setEntries([]));
    return () => {
      current = false;
    };
  }, []);

  return (
    <Section id="history-title" title="History">
      {entries === null ? (
        <CardGridSkeleton count={6} className={GRID} />
      ) : entries.length === 0 ? (
        <p className="rounded-md bg-surface p-6 text-muted shadow-card">{emptyText}</p>
      ) : (
        <ul className={GRID}>
          {entries.map((entry) => (
            <li key={entry.slug}>
              <Link
                href={chapterPath(entry.slug, entry.number)}
                className="group flex flex-col gap-1.5 rounded-md bg-surface p-2 shadow-card"
              >
                <Cover image={entry.series.cover} title={entry.series.title} />
                <span className="line-clamp-1 text-sm font-bold group-hover:text-primary-text">
                  {entry.series.title}
                </span>
                <span className="text-xs text-muted">
                  Ch. {entry.number} · {Math.round(entry.position * 100)}%
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </Section>
  );
}
