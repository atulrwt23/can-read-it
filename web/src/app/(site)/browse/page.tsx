import type { Metadata } from "next";
import Link from "next/link";
import { BrowseFilters } from "@/components/browse/browse-filters";
import { LoadMoreSeries } from "@/components/browse/load-more-series";
import { SeriesGridCard } from "@/components/series-grid-card";
import { browseSeries, getGenres } from "@/lib/api/server";
import { hasFilters, parseBrowseParams } from "@/lib/browse-params";
import { canonical } from "@/lib/site";

type Props = { searchParams: Promise<Record<string, string | string[] | undefined>> };

/** Every filtered view points to /browse as canonical; search results are not worth indexing. */
export async function generateMetadata({ searchParams }: Props): Promise<Metadata> {
  const params = parseBrowseParams(await searchParams);
  return {
    title: params.q ? `Results for “${params.q}”` : "Browse",
    description: "Browse manhwa and web novels by type, status and genre.",
    ...canonical("/browse"),
    ...(params.q ? { robots: { index: false, follow: true } } : {}),
  };
}

export default async function BrowsePage({ searchParams }: Props) {
  const params = parseBrowseParams(await searchParams);
  const [page, genres] = await Promise.all([browseSeries({ ...params, limit: 24 }), getGenres()]);

  const filtered = hasFilters(params);

  return (
    // While new filters load, the old results fade (BrowseFilters marks itself data-pending).
    <main className="mx-auto flex max-w-6xl flex-col gap-6 px-4 py-6 [&:has([data-pending])_[data-results]]:opacity-50">
      <h1 className="text-3xl">{params.q ? `Results for “${params.q}”` : "Browse"}</h1>
      <BrowseFilters params={params} genres={genres} />
      <div className="flex items-center justify-between gap-3 text-sm text-muted">
        <p aria-live="polite">{resultCount(page.total, page.items.length)}</p>
        {filtered && (
          <Link href="/browse" className="font-semibold text-primary-text hover:underline">
            Clear filters
          </Link>
        )}
      </div>
      {page.items.length === 0 ? (
        <div data-results className="py-12 text-center text-muted transition-opacity">
          <p>{params.q ? `Nothing matches “${params.q}”.` : "No series match these filters."}</p>
          {filtered && (
            <p className="mt-2">
              Try fewer filters, or{" "}
              <Link href="/browse" className="font-semibold text-primary-text hover:underline">
                browse everything
              </Link>
              .
            </p>
          )}
        </div>
      ) : (
        <section
          data-results
          aria-label="Results"
          className="grid grid-cols-2 gap-3 transition-opacity sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6"
        >
          {page.items.map((series, index) => (
            <SeriesGridCard key={series.id} series={series} eager={index < 6} />
          ))}
          {page.nextCursor && (
            <LoadMoreSeries key={JSON.stringify(params)} params={params} cursor={page.nextCursor} />
          )}
        </section>
      )}
    </main>
  );
}

/** The API counts matches on the first page; fall back to what is shown. */
function resultCount(total: number | null | undefined, shown: number): string {
  return `${(total ?? shown).toLocaleString("en")} series`;
}
