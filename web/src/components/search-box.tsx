"use client";

import { useRouter } from "next/navigation";
import { useEffect, useId, useRef, useState } from "react";
import { browserApi } from "@/lib/api/browser";
import type { SeriesCard } from "@/lib/api/types";
import { label } from "@/lib/format";
import { seriesPath } from "@/lib/routes";

const MIN_LENGTH = 2;

type Status = "idle" | "loading" | "done" | "error";

/**
 * Header search with type-ahead suggestions (a combobox with a listbox popup). The last option always opens the
 * full results, so Enter works even when suggestions are slow or unavailable.
 */
export function SearchBox({ className = "" }: { className?: string }) {
  const router = useRouter();
  const listId = useId();
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<SeriesCard[]>([]);
  const [status, setStatus] = useState<Status>("idle");
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(-1);
  const request = useRef(0);

  const q = query.trim();
  const searchable = q.length >= MIN_LENGTH;

  useEffect(() => {
    const text = query.trim();
    const id = ++request.current;
    if (text.length < MIN_LENGTH) {
      setResults([]);
      setStatus("idle");
      return;
    }
    setStatus("loading");
    const timer = setTimeout(async () => {
      const { data } = await browserApi
        .GET("/api/v1/series", { params: { query: { q: text, limit: 6 } } })
        .catch(() => ({ data: undefined }));
      if (id !== request.current) return;
      setResults(data?.items ?? []);
      setStatus(data ? "done" : "error");
      setActive(-1);
    }, 250);
    return () => clearTimeout(timer);
  }, [query]);

  const expanded = open && searchable;
  // Suggestions, then "See all results".
  const optionCount = searchable ? results.length + 1 : 0;
  const allResultsPath = `/browse?q=${encodeURIComponent(q)}`;

  function go(path: string) {
    setOpen(false);
    router.push(path);
  }

  function choose(index: number) {
    const series = results[index];
    go(series ? seriesPath(series.slug) : allResultsPath);
  }

  function onKeyDown(event: React.KeyboardEvent<HTMLInputElement>) {
    if (event.key === "ArrowDown" && optionCount > 0) {
      event.preventDefault();
      setOpen(true);
      setActive((i) => (i + 1) % optionCount);
    } else if (event.key === "ArrowUp" && optionCount > 0) {
      event.preventDefault();
      setOpen(true);
      setActive((i) => (i <= 0 ? optionCount - 1 : i - 1));
    } else if (event.key === "Escape") {
      setOpen(false);
      setActive(-1);
    } else if (event.key === "Enter" && expanded && active >= 0) {
      event.preventDefault();
      choose(active);
    }
  }

  return (
    <search className={`relative ${className}`}>
      <form
        action="/browse"
        onSubmit={(event) => {
          event.preventDefault();
          if (q) go(allResultsPath);
        }}
      >
        <input
          type="search"
          name="q"
          value={query}
          maxLength={100}
          onChange={(event) => {
            setQuery(event.target.value);
            setOpen(true);
          }}
          onFocus={() => setOpen(true)}
          onBlur={() => setTimeout(() => setOpen(false), 150)}
          onKeyDown={onKeyDown}
          placeholder="Search titles"
          aria-label="Search series"
          role="combobox"
          aria-expanded={expanded}
          aria-controls={listId}
          aria-autocomplete="list"
          aria-activedescendant={expanded && active >= 0 ? `${listId}-${active}` : undefined}
          autoComplete="off"
          className="h-9 w-full rounded border border-line bg-surface px-3 text-sm placeholder:text-muted focus:border-primary-text focus:outline-none"
        />
        <p role="status" className="sr-only">
          {expanded ? statusText(status, results.length) : ""}
        </p>
        {expanded && (
          <div className="absolute top-full right-0 left-0 z-30 mt-1 overflow-hidden rounded-md border border-line bg-surface shadow-card">
            {status !== "done" || results.length === 0 ? (
              <p className="px-3 py-2 text-sm text-muted">{statusText(status, results.length)}</p>
            ) : null}
            <div id={listId} role="listbox" aria-label="Suggestions">
              {results.map((series, index) => (
                <div
                  key={series.id}
                  id={`${listId}-${index}`}
                  role="option"
                  tabIndex={-1}
                  aria-selected={index === active}
                  onMouseDown={(event) => {
                    event.preventDefault();
                    choose(index);
                  }}
                  onMouseEnter={() => setActive(index)}
                  className={`flex cursor-pointer items-center gap-3 px-3 py-2 ${index === active ? "bg-raised" : ""}`}
                >
                  {series.cover ? (
                    <img
                      src={series.cover.url}
                      alt=""
                      width={32}
                      height={48}
                      className="h-12 w-8 rounded object-cover"
                    />
                  ) : (
                    <span className="h-12 w-8 rounded bg-raised" />
                  )}
                  <span className="min-w-0 flex-1 truncate text-sm font-medium">
                    {series.title}
                  </span>
                  <span className="text-xs text-muted">{label(series.type)}</span>
                </div>
              ))}
              <div
                id={`${listId}-${results.length}`}
                role="option"
                tabIndex={-1}
                aria-selected={active === results.length}
                onMouseDown={(event) => {
                  event.preventDefault();
                  choose(results.length);
                }}
                onMouseEnter={() => setActive(results.length)}
                className={`cursor-pointer border-t border-line px-3 py-2 text-sm font-semibold text-primary-text ${active === results.length ? "bg-raised" : ""}`}
              >
                See all results for “{q}”
              </div>
            </div>
          </div>
        )}
      </form>
    </search>
  );
}

function statusText(status: Status, count: number): string {
  switch (status) {
    case "loading":
      return "Searching…";
    case "error":
      return "Suggestions are unavailable. Press Enter to search.";
    case "done":
      return count === 0
        ? "No titles match yet. Press Enter to search everything."
        : `${count} suggestions`;
    default:
      return "";
  }
}
