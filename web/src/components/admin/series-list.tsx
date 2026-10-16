"use client";

import Link from "next/link";
import { type FormEvent, useEffect, useState } from "react";
import { Button, Notice, TextField } from "@/components/auth/form";
import { Badge } from "@/components/badge";
import { Skeleton } from "@/components/skeleton";
import { type AdminSeriesItem, admin } from "@/lib/admin/client";
import { errorMessage } from "@/lib/auth/client";
import { label, relativeTime } from "@/lib/format";
import { Section } from "./ui";

const PAGE = 50;

/** Every series, drafts included, most recently edited first. */
export function SeriesList() {
  const [query, setQuery] = useState("");
  const [applied, setApplied] = useState("");
  const [items, setItems] = useState<AdminSeriesItem[] | null>(null);
  const [total, setTotal] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [loadingMore, setLoadingMore] = useState(false);

  useEffect(() => {
    let current = true;
    setItems(null);
    setError(null);
    admin
      .listSeries(applied)
      .then((page) => {
        if (!current) return;
        setItems(page.items);
        setTotal(page.total);
      })
      .catch((caught) => current && setError(errorMessage(caught)));
    return () => {
      current = false;
    };
  }, [applied]);

  function search(event: FormEvent) {
    event.preventDefault();
    setApplied(query);
  }

  async function loadMore() {
    if (!items) return;
    setLoadingMore(true);
    try {
      const page = await admin.listSeries(applied, items.length, PAGE);
      setItems([...items, ...page.items]);
      setTotal(page.total);
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setLoadingMore(false);
    }
  }

  return (
    <Section title="Series">
      <search>
        <form onSubmit={search} className="flex items-end gap-2">
          <div className="flex-1">
            <TextField
              id="series-search"
              label="Find a series"
              type="search"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
            />
          </div>
          <Button variant="secondary">Search</Button>
        </form>
      </search>
      {error ? <Notice kind="error">{error}</Notice> : null}
      {items === null && !error ? (
        <>
          <p role="status" className="sr-only">
            Loading series…
          </p>
          <Skeleton className="h-16 w-full" />
          <Skeleton className="h-16 w-full" />
          <Skeleton className="h-16 w-full" />
        </>
      ) : null}
      {items && items.length === 0 ? (
        <p className="text-muted">
          {applied ? "No series match." : "No series yet. Create the first one."}
        </p>
      ) : null}
      {items && items.length > 0 ? (
        <>
          <p className="text-sm text-muted">{total === 1 ? "1 series" : `${total} series`}</p>
          <ul className="flex flex-col divide-y divide-line">
            {items.map((series) => (
              <li key={series.id} className="flex items-center gap-3 py-3">
                {series.cover ? (
                  <img
                    src={series.cover.url}
                    width={series.cover.width}
                    height={series.cover.height}
                    alt=""
                    className="h-16 w-11 shrink-0 rounded object-cover"
                  />
                ) : (
                  <div aria-hidden="true" className="h-16 w-11 shrink-0 rounded bg-raised" />
                )}
                <div className="flex min-w-0 flex-1 flex-col gap-1">
                  <Link
                    href={`/admin/series/${series.id}`}
                    className="truncate font-semibold hover:underline"
                  >
                    {series.title}
                  </Link>
                  <div className="flex flex-wrap items-center gap-2 text-sm text-muted">
                    <Badge tone={series.visibility === "PUBLISHED" ? "success" : "neutral"}>
                      {label(series.visibility)}
                    </Badge>
                    <span>{label(series.type)}</span>
                    <span>
                      {series.chapterCount === 1 ? "1 chapter" : `${series.chapterCount} chapters`}
                    </span>
                    <span>edited {relativeTime(series.updatedAt)}</span>
                  </div>
                </div>
              </li>
            ))}
          </ul>
          {items.length < total ? (
            <Button
              type="button"
              variant="secondary"
              busy={loadingMore}
              onClick={() => void loadMore()}
              className="self-center"
            >
              Load more
            </Button>
          ) : null}
        </>
      ) : null}
    </Section>
  );
}
