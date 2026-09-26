"use client";

import { usePathname, useRouter } from "next/navigation";
import { useTransition } from "react";
import type { BrowseParams } from "@/lib/api/server";
import type { Genre } from "@/lib/api/types";
import { toSearchParams } from "@/lib/browse-params";

const selectClass =
  "h-9 rounded border border-line bg-surface px-2 text-sm focus:border-primary-text focus:outline-none";

/** Filter bar. Every change is written to the URL, so filtered views can be shared and cached. */
export function BrowseFilters({ params, genres }: { params: BrowseParams; genres: Genre[] }) {
  const router = useRouter();
  const pathname = usePathname();
  const [pending, startTransition] = useTransition();

  function update(change: Partial<BrowseParams>) {
    const search = toSearchParams({ ...params, ...change }).toString();
    startTransition(() =>
      router.replace(search ? `${pathname}?${search}` : pathname, { scroll: false }),
    );
  }

  function toggleGenre(slug: string) {
    const current = params.genres ?? [];
    const next = current.includes(slug) ? current.filter((g) => g !== slug) : [...current, slug];
    update({ genres: next.length > 0 ? next : undefined });
  }

  return (
    <div className="flex flex-col gap-3 rounded-md bg-surface p-4 shadow-card" aria-busy={pending}>
      <search>
        <form
          onSubmit={(event) => {
            event.preventDefault();
            const q = new FormData(event.currentTarget).get("q")?.toString().trim();
            update({ q: q || undefined });
          }}
          className="flex gap-2"
        >
          <input
            type="search"
            name="q"
            defaultValue={params.q ?? ""}
            key={params.q ?? ""}
            placeholder="Search titles and synopses"
            aria-label="Search series"
            className="h-9 min-w-0 flex-1 rounded border border-line bg-surface px-3 text-sm placeholder:text-muted focus:border-primary-text focus:outline-none"
          />
          <button
            type="submit"
            className="rounded bg-primary px-4 text-sm font-semibold text-on-primary"
          >
            Search
          </button>
        </form>
      </search>
      <div className="flex flex-wrap gap-2">
        <label className="flex items-center gap-2 text-sm">
          <span className="text-muted">Type</span>
          <select
            className={selectClass}
            value={params.type ?? ""}
            onChange={(e) =>
              update({ type: (e.target.value || undefined) as BrowseParams["type"] })
            }
          >
            <option value="">All</option>
            <option value="MANHWA">Manhwa</option>
            <option value="NOVEL">Novels</option>
          </select>
        </label>
        <label className="flex items-center gap-2 text-sm">
          <span className="text-muted">Status</span>
          <select
            className={selectClass}
            value={params.status ?? ""}
            onChange={(e) =>
              update({ status: (e.target.value || undefined) as BrowseParams["status"] })
            }
          >
            <option value="">Any</option>
            <option value="ONGOING">Ongoing</option>
            <option value="COMPLETED">Completed</option>
            <option value="HIATUS">Hiatus</option>
          </select>
        </label>
        <label className="flex items-center gap-2 text-sm sm:ml-auto">
          <span className="text-muted">Sort</span>
          <select
            className={selectClass}
            value={params.sort ?? "UPDATED"}
            onChange={(e) => update({ sort: e.target.value as BrowseParams["sort"] })}
          >
            <option value="UPDATED">Latest update</option>
            <option value="NEWEST">Newest</option>
            <option value="TITLE">Title A–Z</option>
          </select>
        </label>
      </div>
      <fieldset>
        <legend className="mb-2 text-sm text-muted">Genres (all selected must match)</legend>
        <div className="flex flex-wrap gap-2">
          {genres.map((genre) => {
            const selected = params.genres?.includes(genre.slug) ?? false;
            return (
              <button
                key={genre.slug}
                type="button"
                aria-pressed={selected}
                onClick={() => toggleGenre(genre.slug)}
                className={`rounded-full border px-3 py-1 text-sm font-medium ${
                  selected
                    ? "border-primary bg-primary text-on-primary"
                    : "border-line bg-surface text-muted hover:text-text"
                }`}
              >
                {genre.name}
              </button>
            );
          })}
        </div>
      </fieldset>
    </div>
  );
}
