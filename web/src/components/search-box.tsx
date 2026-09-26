"use client";

import { useRouter } from "next/navigation";
import { useEffect, useId, useRef, useState } from "react";
import { browserApi } from "@/lib/api/browser";
import type { SeriesCard } from "@/lib/api/types";
import { label } from "@/lib/format";
import { seriesPath } from "@/lib/routes";

/** Header search with type-ahead suggestions (a combobox with a listbox popup). */
export function SearchBox({ className = "" }: { className?: string }) {
  const router = useRouter();
  const listId = useId();
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<SeriesCard[]>([]);
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(-1);
  const request = useRef(0);

  useEffect(() => {
    const q = query.trim();
    if (q.length < 2) {
      setResults([]);
      return;
    }
    const id = ++request.current;
    const timer = setTimeout(async () => {
      const { data } = await browserApi.GET("/api/v1/series", {
        params: { query: { q, limit: 6 } },
      });
      if (id === request.current) {
        setResults(data?.items ?? []);
        setActive(-1);
      }
    }, 250);
    return () => clearTimeout(timer);
  }, [query]);

  const expanded = open && results.length > 0;

  function go(path: string) {
    setOpen(false);
    router.push(path);
  }

  function onKeyDown(event: React.KeyboardEvent<HTMLInputElement>) {
    if (event.key === "ArrowDown" && results.length > 0) {
      event.preventDefault();
      setOpen(true);
      setActive((i) => (i + 1) % results.length);
    } else if (event.key === "ArrowUp" && results.length > 0) {
      event.preventDefault();
      setActive((i) => (i <= 0 ? results.length - 1 : i - 1));
    } else if (event.key === "Escape") {
      setOpen(false);
    } else if (event.key === "Enter" && expanded && active >= 0) {
      event.preventDefault();
      const selected = results[active];
      if (selected) go(seriesPath(selected.slug));
    }
  }

  return (
    <search className={`relative ${className}`}>
      <form
        action="/browse"
        onSubmit={(event) => {
          event.preventDefault();
          go(`/browse?q=${encodeURIComponent(query.trim())}`);
        }}
      >
        <input
          type="search"
          name="q"
          value={query}
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
          aria-activedescendant={active >= 0 ? `${listId}-${active}` : undefined}
          autoComplete="off"
          className="h-9 w-full rounded border border-line bg-surface px-3 text-sm placeholder:text-muted focus:border-primary-text focus:outline-none"
        />
        {expanded && (
          <div
            id={listId}
            role="listbox"
            aria-label="Suggestions"
            className="absolute top-full right-0 left-0 z-30 mt-1 overflow-hidden rounded-md border border-line bg-surface shadow-card"
          >
            {results.map((series, index) => (
              <div
                key={series.id}
                id={`${listId}-${index}`}
                role="option"
                tabIndex={-1}
                aria-selected={index === active}
                onMouseDown={(event) => {
                  event.preventDefault();
                  go(seriesPath(series.slug));
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
                <span className="min-w-0 flex-1 truncate text-sm font-medium">{series.title}</span>
                <span className="text-xs text-muted">{label(series.type)}</span>
              </div>
            ))}
          </div>
        )}
      </form>
    </search>
  );
}
