import type { Metadata } from "next";
import { BrowseFilters } from "@/components/browse/browse-filters";
import { LoadMoreSeries } from "@/components/browse/load-more-series";
import { SeriesGridCard } from "@/components/series-grid-card";
import { browseSeries, getGenres } from "@/lib/api/server";
import { parseBrowseParams } from "@/lib/browse-params";

export const metadata: Metadata = { title: "Browse" };

export default async function BrowsePage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const params = parseBrowseParams(await searchParams);
  const [page, genres] = await Promise.all([browseSeries({ ...params, limit: 24 }), getGenres()]);

  return (
    <main className="mx-auto flex max-w-6xl flex-col gap-6 px-4 py-6">
      <h1 className="text-3xl">{params.q ? `Results for “${params.q}”` : "Browse"}</h1>
      <BrowseFilters params={params} genres={genres} />
      {page.items.length === 0 ? (
        <p className="py-12 text-center text-muted">No series match these filters.</p>
      ) : (
        <section
          aria-label="Results"
          className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6"
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
