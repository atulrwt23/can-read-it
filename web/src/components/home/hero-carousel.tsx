"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { Badge } from "@/components/badge";
import { Cover } from "@/components/cover";
import type { SeriesCard } from "@/lib/api/types";
import { label } from "@/lib/format";
import { seriesPath } from "@/lib/routes";

const INTERVAL_MS = 6000;
/** A horizontal drag at least this long (px) changes the slide. */
const SWIPE_PX = 40;

/**
 * Featured series. Auto-advances unless the reader prefers reduced motion or is interacting. Swipe, the arrow
 * buttons, the dots, or the left and right arrow keys change the slide.
 */
export function HeroCarousel({ items }: { items: SeriesCard[] }) {
  const [index, setIndex] = useState(0);
  const [paused, setPaused] = useState(false);
  const swipeStart = useRef<{ x: number; y: number } | null>(null);

  const show = (delta: number) => setIndex((i) => (i + delta + items.length) % items.length);

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
      onKeyDown={(event) => {
        if (items.length < 2) return;
        if (event.key === "ArrowLeft") show(-1);
        if (event.key === "ArrowRight") show(1);
      }}
      // Vertical drags still scroll the page; horizontal ones swipe.
      style={{ touchAction: "pan-y" }}
      onPointerDown={(event) => {
        swipeStart.current = { x: event.clientX, y: event.clientY };
      }}
      onPointerUp={(event) => {
        const start = swipeStart.current;
        swipeStart.current = null;
        if (!start || items.length < 2) return;
        const dx = event.clientX - start.x;
        if (Math.abs(dx) >= SWIPE_PX && Math.abs(dx) > Math.abs(event.clientY - start.y)) {
          setPaused(true);
          show(dx < 0 ? 1 : -1);
        }
      }}
      onPointerCancel={() => {
        swipeStart.current = null;
      }}
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
            onClick={() => show(-1)}
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
            onClick={() => show(1)}
            className="inline-flex size-8 items-center justify-center rounded bg-surface/80 text-text hover:bg-surface"
          >
            ›
          </button>
        </div>
      )}
    </section>
  );
}
