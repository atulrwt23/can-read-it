"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { Cover } from "@/components/cover";
import { type RecentEntry, recentlyRead } from "@/lib/reading/store";
import { chapterPath } from "@/lib/routes";

const SHOWN = 6;

/**
 * The reader's recently read series, from the account when signed in, else from browser storage. The home page is
 * the same for everyone, so this renders nothing on the server and fills in after hydration.
 */
export function ContinueReading() {
  const [entries, setEntries] = useState<RecentEntry[]>([]);

  useEffect(() => {
    let current = true;
    recentlyRead(SHOWN)
      .then((recent) => current && setEntries(recent))
      .catch(() => undefined);
    return () => {
      current = false;
    };
  }, []);

  if (entries.length === 0) return null;

  return (
    <section aria-labelledby="continue-title">
      <h2 id="continue-title" className="mb-3 text-xl">
        Continue reading
      </h2>
      <ul className="grid grid-cols-3 gap-3 sm:grid-cols-4 md:grid-cols-6">
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
              <span className="text-xs text-muted">Ch. {entry.number}</span>
              <span
                aria-hidden="true"
                className="h-1 overflow-hidden rounded-full bg-raised"
                title={`${Math.round(entry.position * 100)}% of the chapter`}
              >
                <span
                  className="block h-full bg-primary"
                  style={{ width: `${Math.max(4, Math.round(entry.position * 100))}%` }}
                />
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </section>
  );
}
