import type { Metadata } from "next";
import Link from "next/link";
import { permanentRedirect } from "next/navigation";
import { NovelBody, NovelSettingsButton } from "@/components/reader/novel-reader";
import { ReaderBar } from "@/components/reader/reader-bar";
import { ReadingTracker } from "@/components/reader/reading-tracker";
import { getChapter, getChapters } from "@/lib/api/server";
import { chapterPath, seriesPath } from "@/lib/routes";

export const revalidate = 300;

// Chapters are rendered on first request and then cached (ISR); none are built ahead.
export function generateStaticParams() {
  return [];
}

type Props = { params: Promise<{ slug: string; number: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { slug, number } = await params;
  const chapter = await getChapter(slug, number);
  return { title: `${chapter.series.title} · Chapter ${chapter.number}` };
}

export default async function ChapterPage({ params }: Props) {
  const { slug, number } = await params;
  const [chapter, list] = await Promise.all([
    getChapter(slug, number),
    getChapters(slug, { order: "ASC", limit: 200 }),
  ]);
  if (chapter.number !== number) {
    // One URL per chapter: /chapter/10.00 and /chapter/10 are the same page.
    permanentRedirect(chapterPath(slug, chapter.number));
  }
  const isNovel = chapter.series.type === "NOVEL";

  return (
    <>
      <ReaderBar
        slug={slug}
        seriesTitle={chapter.series.title}
        number={chapter.number}
        chapters={list.items}
        previous={chapter.previous}
        next={chapter.next}
        actions={isNovel ? <NovelSettingsButton /> : undefined}
      />
      <main className="pt-14 pb-12 sm:pb-0">
        <h1 className="sr-only">
          {chapter.series.title}, chapter {chapter.number}
          {chapter.title ? `: ${chapter.title}` : ""}
        </h1>
        {chapter.locked ? (
          <div className="mx-auto my-16 max-w-md rounded-md bg-surface p-6 text-center shadow-card">
            <h2 className="mb-2 text-2xl">Early access chapter</h2>
            {/* TODO(phase 3): unlock with coins once monetization ships. */}
            <p className="text-muted">
              This chapter is in early access. Unlocking with coins is coming soon.
            </p>
          </div>
        ) : isNovel ? (
          <div className="px-3 py-6">
            <p className="mx-auto mb-4 max-w-[70ch] text-center text-sm font-semibold tracking-wide text-muted uppercase">
              Chapter {chapter.number}
              {chapter.title && chapter.title !== `Chapter ${chapter.number}`
                ? ` · ${chapter.title}`
                : ""}
            </p>
            {chapter.novel ? (
              <NovelBody markdown={chapter.novel.markdown} />
            ) : (
              <p className="text-center text-muted">This chapter has no text yet.</p>
            )}
          </div>
        ) : (
          <div className="mx-auto max-w-[800px] bg-surface">
            {chapter.pages.map((page, index) => (
              <img
                key={page.url}
                src={page.url}
                width={page.width}
                height={page.height}
                alt={`Page ${index + 1}`}
                loading={index < 2 ? "eager" : "lazy"}
                fetchPriority={index === 0 ? "high" : undefined}
                decoding="async"
                className="block h-auto w-full"
              />
            ))}
          </div>
        )}
        <nav aria-label="End of chapter" className="mx-auto my-10 max-w-md px-4 text-center">
          <p className="mb-4 text-sm text-muted">End of chapter {chapter.number}</p>
          <div className="flex justify-center gap-3">
            <Link
              href={seriesPath(slug)}
              className="rounded border border-line px-4 py-2 text-sm font-semibold hover:bg-raised"
            >
              All chapters
            </Link>
            {chapter.next ? (
              <Link
                href={chapterPath(slug, chapter.next)}
                className="rounded bg-primary px-4 py-2 text-sm font-semibold text-on-primary hover:brightness-95"
              >
                Next: Ch. {chapter.next}
              </Link>
            ) : (
              <span className="rounded bg-raised px-4 py-2 text-sm font-semibold text-muted">
                You are caught up
              </span>
            )}
          </div>
        </nav>
      </main>
      <ReadingTracker slug={slug} number={chapter.number} />
    </>
  );
}
