"use client";

import Link from "next/link";
import { useId, useRef, useState } from "react";
import type { HomeResponse } from "@/lib/api/types";
import { compactNumber } from "@/lib/format";
import { seriesPath } from "@/lib/routes";

const TABS = [
  { key: "today", label: "Today" },
  { key: "week", label: "Week" },
  { key: "allTime", label: "All time" },
] as const;

type TabKey = (typeof TABS)[number]["key"];

/** Ranked popular series with Today / Week / All-time tabs (ARIA tabs with arrow-key support). */
export function PopularPanel({ popular }: { popular: HomeResponse["popular"] }) {
  const baseId = useId();
  const [selected, setSelected] = useState<TabKey>("today");
  const tabs = useRef<(HTMLButtonElement | null)[]>([]);

  function onKeyDown(event: React.KeyboardEvent, index: number) {
    const delta = event.key === "ArrowRight" ? 1 : event.key === "ArrowLeft" ? -1 : 0;
    if (delta === 0) return;
    const next = (index + delta + TABS.length) % TABS.length;
    setSelected(TABS[next]?.key ?? "today");
    tabs.current[next]?.focus();
  }

  return (
    <section aria-labelledby={`${baseId}-title`} className="rounded-md bg-surface p-4 shadow-card">
      <h2 id={`${baseId}-title`} className="mb-3 text-xl">
        Popular
      </h2>
      <div
        role="tablist"
        aria-label="Ranking period"
        className="mb-3 flex gap-1 rounded bg-raised p-1"
      >
        {TABS.map((tab, index) => (
          <button
            key={tab.key}
            ref={(element) => {
              tabs.current[index] = element;
            }}
            type="button"
            role="tab"
            id={`${baseId}-tab-${tab.key}`}
            aria-selected={selected === tab.key}
            aria-controls={`${baseId}-panel`}
            tabIndex={selected === tab.key ? 0 : -1}
            onClick={() => setSelected(tab.key)}
            onKeyDown={(event) => onKeyDown(event, index)}
            className={`flex-1 rounded px-2 py-1 text-sm font-semibold ${
              selected === tab.key
                ? "bg-surface text-text shadow-card"
                : "text-muted hover:text-text"
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>
      <ol
        id={`${baseId}-panel`}
        role="tabpanel"
        aria-labelledby={`${baseId}-tab-${selected}`}
        className="flex flex-col gap-3"
      >
        {popular[selected].length === 0 && <li className="text-sm text-muted">No rankings yet.</li>}
        {popular[selected].map((entry) => (
          <li key={entry.series.id} className="flex items-center gap-3">
            <span
              className={`w-6 shrink-0 text-center text-lg font-extrabold ${entry.rank <= 3 ? "text-primary-text" : "text-muted"}`}
            >
              {entry.rank}
            </span>
            {entry.series.cover ? (
              <img
                src={entry.series.cover.url}
                alt=""
                width={40}
                height={60}
                loading="lazy"
                className="h-15 w-10 shrink-0 rounded object-cover"
              />
            ) : (
              <span className="h-15 w-10 shrink-0 rounded bg-raised" />
            )}
            <div className="min-w-0">
              <Link
                href={seriesPath(entry.series.slug)}
                className="line-clamp-2 text-sm font-semibold hover:text-primary-text"
              >
                {entry.series.title}
              </Link>
              <p className="text-xs text-muted">{compactNumber(entry.views)} views</p>
            </div>
          </li>
        ))}
      </ol>
    </section>
  );
}
