"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { Notice, SelectField } from "@/components/auth/form";
import { Skeleton } from "@/components/skeleton";
import { type AnalyticsSummary, admin } from "@/lib/admin/client";
import { errorMessage } from "@/lib/auth/client";
import { seriesPath } from "@/lib/routes";
import { Section } from "./ui";

/** Short column headings for the event names. */
const LABELS: Record<string, string> = {
  PAGE_VIEW: "Page views",
  SERIES_VIEW: "Series views",
  CHAPTER_OPEN: "Chapters opened",
  CHAPTER_FINISH: "Chapters finished",
  SEARCH: "Searches",
  FOLLOW: "Follows",
  SIGN_UP: "Sign-ups",
};

const RANGES = [7, 30, 90];

/** Daily event counts and the most read series: anonymous numbers only (docs/modules/analytics.md). */
export function AnalyticsView() {
  const [days, setDays] = useState(30);
  const [summary, setSummary] = useState<AnalyticsSummary | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let current = true;
    setSummary(null);
    setError(null);
    admin
      .analytics(days)
      .then((loaded) => current && setSummary(loaded))
      .catch((caught) => current && setError(errorMessage(caught)));
    return () => {
      current = false;
    };
  }, [days]);

  const rows = summary ? [...summary.days].reverse() : [];
  const totals = summary
    ? summary.names.map((name) =>
        summary.days.reduce((sum, day) => sum + (day.counts[name]?.events ?? 0), 0),
      )
    : [];

  return (
    <>
      <div className="w-48">
        <SelectField
          id="range"
          label="Period"
          value={days}
          onChange={(event) => setDays(Number(event.target.value))}
        >
          {RANGES.map((range) => (
            <option key={range} value={range}>
              Last {range} days
            </option>
          ))}
        </SelectField>
      </div>
      {error ? <Notice kind="error">{error}</Notice> : null}
      {!summary && !error ? (
        <>
          <p role="status" className="sr-only">
            Loading analytics…
          </p>
          <Skeleton className="h-64 w-full rounded-md" />
        </>
      ) : null}
      {summary ? (
        <>
          <Section title="Most opened series">
            <p className="text-sm text-muted">Chapter opens over the last 7 days.</p>
            {summary.topSeries.length === 0 ? (
              <p className="text-muted">No chapters opened yet.</p>
            ) : (
              <ol className="flex flex-col divide-y divide-line">
                {summary.topSeries.map((series, index) => (
                  <li key={series.slug} className="flex items-center gap-3 py-2">
                    <span className="w-6 text-right font-bold text-muted">{index + 1}</span>
                    <Link
                      href={seriesPath(series.slug)}
                      className="min-w-0 flex-1 truncate hover:underline"
                    >
                      {series.title}
                    </Link>
                    <span className="text-sm">{series.opens.toLocaleString("en")} opens</span>
                  </li>
                ))}
              </ol>
            )}
          </Section>
          <Section title="Daily counts">
            <p className="text-sm text-muted">
              Events, with distinct visitors in brackets. Today is counted live; a visitor is
              counted once per day.
            </p>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[640px] border-collapse text-sm">
                <caption className="sr-only">Events per day</caption>
                <thead>
                  <tr className="border-b border-line text-left">
                    <th scope="col" className="py-2 pr-3 font-semibold">
                      Day
                    </th>
                    {summary.names.map((name) => (
                      <th key={name} scope="col" className="px-2 py-2 text-right font-semibold">
                        {LABELS[name] ?? name}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {rows.map((day) => (
                    <tr key={day.day} className="border-b border-line">
                      <th
                        scope="row"
                        className="whitespace-nowrap py-1.5 pr-3 text-left font-normal"
                      >
                        {day.day}
                      </th>
                      {summary.names.map((name) => {
                        const count = day.counts[name];
                        return (
                          <td key={name} className="px-2 py-1.5 text-right tabular-nums">
                            {count ? (
                              <>
                                {count.events.toLocaleString("en")}
                                {name !== "SIGN_UP" ? (
                                  <span className="text-muted">
                                    {" "}
                                    ({count.visitors.toLocaleString("en")})
                                  </span>
                                ) : null}
                              </>
                            ) : (
                              <span className="text-muted">0</span>
                            )}
                          </td>
                        );
                      })}
                    </tr>
                  ))}
                </tbody>
                <tfoot>
                  <tr className="font-semibold">
                    <th scope="row" className="py-2 pr-3 text-left">
                      Total
                    </th>
                    {totals.map((total, index) => (
                      <td key={summary.names[index]} className="px-2 py-2 text-right tabular-nums">
                        {total.toLocaleString("en")}
                      </td>
                    ))}
                  </tr>
                </tfoot>
              </table>
            </div>
          </Section>
        </>
      ) : null}
    </>
  );
}
