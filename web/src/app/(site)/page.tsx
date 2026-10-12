import Link from "next/link";
import { ContinueReading } from "@/components/home/continue-reading";
import { HeroCarousel } from "@/components/home/hero-carousel";
import { PopularPanel } from "@/components/home/popular-panel";
import { SeriesGridCard } from "@/components/series-grid-card";
import { getHome } from "@/lib/api/server";

export const revalidate = 60;

export default async function HomePage() {
  const home = await getHome();
  if (!home) {
    return (
      <main className="mx-auto max-w-6xl px-4 py-16 text-center text-muted">
        The catalogue is warming up. Refresh in a moment.
      </main>
    );
  }
  return (
    <main className="mx-auto flex max-w-6xl flex-col gap-8 px-4 py-6">
      <HeroCarousel items={home.featured} />
      <ContinueReading />
      <div className="grid gap-6 lg:grid-cols-[1fr_320px]">
        <section aria-labelledby="latest-title">
          <div className="mb-3 flex items-baseline justify-between gap-3">
            <h2 id="latest-title" className="text-xl">
              Latest updates
            </h2>
            <Link
              href="/browse"
              className="text-sm font-semibold text-primary-text hover:underline"
            >
              View all<span className="sr-only"> latest updates</span>
            </Link>
          </div>
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4">
            {home.latestUpdates.map((series, index) => (
              <SeriesGridCard key={series.id} series={series} eager={index < 4} />
            ))}
          </div>
        </section>
        <aside>
          <PopularPanel popular={home.popular} />
        </aside>
      </div>
    </main>
  );
}
