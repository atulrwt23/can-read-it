import type { Metadata } from "next";
import Link from "next/link";
import { Badge } from "@/components/badge";
import { Cover } from "@/components/cover";
import { ChapterList } from "@/components/series/chapter-list";
import { ContinueButton } from "@/components/series/continue-button";
import { ExpandableText } from "@/components/series/expandable-text";
import { getChapters, getSeries } from "@/lib/api/server";
import { label } from "@/lib/format";

export const revalidate = 60;

// Series pages are rendered on first request and then cached (ISR); none are built ahead.
export function generateStaticParams() {
  return [];
}

type Props = { params: Promise<{ slug: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const series = await getSeries((await params).slug);
  return {
    title: series.title,
    description: series.synopsis.slice(0, 160),
    openGraph: { title: series.title, images: series.cover ? [series.cover.url] : [] },
  };
}

export default async function SeriesPage({ params }: Props) {
  const { slug } = await params;
  const [series, chapters] = await Promise.all([
    getSeries(slug),
    getChapters(slug, { limit: 100 }),
  ]);
  const statusTone =
    series.status === "COMPLETED" ? "success" : series.status === "HIATUS" ? "warning" : "neutral";

  return (
    <main>
      <div className="relative overflow-hidden border-b border-line bg-raised">
        {series.cover && (
          <img
            src={series.cover.url}
            alt=""
            aria-hidden="true"
            className="absolute inset-0 size-full scale-110 object-cover opacity-50 blur-2xl"
          />
        )}
        <div className="absolute inset-0 bg-gradient-to-t from-bg via-bg/80 to-bg/30" />
        <div className="relative mx-auto flex max-w-6xl flex-col gap-6 px-4 py-8 sm:flex-row">
          <div className="w-40 shrink-0 self-center sm:w-52 sm:self-start">
            <Cover image={series.cover} title={series.title} eager className="shadow-card" />
          </div>
          <div className="flex min-w-0 flex-col gap-3">
            <div className="flex flex-wrap gap-1.5">
              <Badge tone="primary">{label(series.type)}</Badge>
              <Badge tone={statusTone}>{label(series.status)}</Badge>
              {series.ageRating !== "ALL" && <Badge>{label(series.ageRating)}</Badge>}
            </div>
            <h1 className="text-3xl md:text-4xl">{series.title}</h1>
            {series.altTitles.length > 0 && (
              <p className="text-sm text-muted">{series.altTitles.join(" · ")}</p>
            )}
            <ul className="flex flex-wrap gap-2" aria-label="Genres">
              {series.genres.map((genre) => (
                <li key={genre.slug}>
                  <Link
                    href={`/browse?genres=${genre.slug}`}
                    className="rounded-full border border-line bg-surface px-3 py-0.5 text-sm hover:text-primary-text"
                  >
                    {genre.name}
                  </Link>
                </li>
              ))}
            </ul>
            <dl className="flex flex-wrap gap-x-6 gap-y-1 text-sm">
              <div className="flex gap-1">
                <dt className="text-muted">Chapters</dt>
                <dd className="font-semibold">{series.chapters.count}</dd>
              </div>
              {series.releaseCadence && (
                <div className="flex gap-1">
                  <dt className="text-muted">Releases</dt>
                  <dd className="font-semibold">{series.releaseCadence}</dd>
                </div>
              )}
              {series.firstReleasedYear && (
                <div className="flex gap-1">
                  <dt className="text-muted">Since</dt>
                  <dd className="font-semibold">{series.firstReleasedYear}</dd>
                </div>
              )}
            </dl>
            <div className="flex flex-wrap items-center gap-3">
              <ContinueButton slug={series.slug} first={series.chapters.first} />
              {/* TODO(step 4): Follow button once reading/follows ship. */}
            </div>
          </div>
        </div>
      </div>
      <div className="mx-auto grid max-w-6xl gap-6 px-4 py-6 lg:grid-cols-[1fr_2fr]">
        <section
          aria-labelledby="synopsis-title"
          className="rounded-md bg-surface p-4 shadow-card lg:self-start"
        >
          <h2 id="synopsis-title" className="mb-2 text-xl">
            Synopsis
          </h2>
          <ExpandableText text={series.synopsis} />
        </section>
        <ChapterList slug={series.slug} initial={chapters} now={new Date().toISOString()} />
      </div>
    </main>
  );
}
