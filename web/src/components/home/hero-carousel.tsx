"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { Badge } from "@/components/badge";
import { Cover } from "@/components/cover";
import type { SeriesCard } from "@/lib/api/types";
import { label } from "@/lib/format";
import { seriesPath } from "@/lib/routes";

const INTERVAL_MS = 6000;

/** Featured series. Auto-advances unless the reader prefers reduced motion or is interacting. */
export function HeroCarousel({ items }: { items: SeriesCard[] }) {
  const [index, setIndex] = useState(0);
  const [paused, setPaused] = useState(false);

  useEffect(() => {
    if (paused || items.length < 2 || matchMedia("(prefers-reduced-motion: reduce)").matches) {
      return;
    }
    const timer = setInterval(() => setIndex((i) => (i + 1) % items.length), INTERVAL_MS);
    return () => clearInterval(timer);
  }, [paused, items.length]);

  const current = items[index];
  if (!current) return null;

  return (
    <section
      aria-roledescription="carousel"
      aria-label="Featured series"
      className="relative overflow-hidden rounded-md bg-raised shadow-card"
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
      onFocus={() => setPaused(true)}
      onBlur={() => setPaused(false)}
    >
      {current.cover && (
        <img
          src={current.cover.url}
          alt=""
          aria-hidden="true"
          className="absolute inset-0 size-full scale-110 object-cover opacity-60 blur-2xl"
        />
      )}
      <div className="absolute inset-0 bg-gradient-to-r from-bg/95 via-bg/80 to-bg/40" />
      <article
        aria-roledescription="slide"
        aria-label={`${index + 1} of ${items.length}`}
        className="relative flex gap-6 p-6 md:p-8"
      >
        <div className="w-28 shrink-0 sm:w-40">
          <Cover image={current.cover} title={current.title} eager className="shadow-card" />
        </div>
        <div className="flex min-w-0 flex-col justify-center gap-3">
          <div className="flex flex-wrap gap-1.5">
            <Badge tone="primary">{label(current.type)}</Badge>
            {current.genres.slice(0, 3).map((genre) => (
              <Badge key={genre.slug}>{genre.name}</Badge>
            ))}
          </div>
          <h2 className="text-2xl md:text-4xl">{current.title}</h2>
          <p className="line-clamp-3 max-w-prose text-muted">{current.synopsis}</p>
          <div>
            <Link
              href={seriesPath(current.slug)}
              className="inline-block rounded bg-primary px-4 py-2 text-sm font-semibold text-on-primary hover:brightness-95"
            >
              Read now
            </Link>
          </div>
        </div>
      </article>
      {items.length > 1 && (
        <div className="relative flex items-center justify-end gap-2 px-6 pb-4">
          <button
            type="button"
            aria-label="Previous slide"
            onClick={() => setIndex((i) => (i - 1 + items.length) % items.length)}
            className="inline-flex size-8 items-center justify-center rounded bg-surface/80 text-text hover:bg-surface"
          >
            ‹
          </button>
          {items.map((item, i) => (
            <button
              key={item.id}
              type="button"
              aria-label={`Show ${item.title}`}
              aria-current={i === index}
              onClick={() => setIndex(i)}
              className={`h-2 rounded-full transition-all ${i === index ? "w-6 bg-primary" : "w-2 bg-line"}`}
            />
          ))}
          <button
            type="button"
            aria-label="Next slide"
            onClick={() => setIndex((i) => (i + 1) % items.length)}
            className="inline-flex size-8 items-center justify-center rounded bg-surface/80 text-text hover:bg-surface"
          >
            ›
          </button>
        </div>
      )}
    </section>
  );
}
