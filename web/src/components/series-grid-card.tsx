import Link from "next/link";
import { Badge } from "@/components/badge";
import { Cover } from "@/components/cover";
import type { SeriesCard } from "@/lib/api/types";
import { isNew, label, relativeTime } from "@/lib/format";
import { chapterPath, seriesPath } from "@/lib/routes";

/** A grid card: cover, title and the newest chapters with release times (server-rendered). */
export function SeriesGridCard({ series, eager = false }: { series: SeriesCard; eager?: boolean }) {
  return (
    <article className="flex flex-col gap-2 rounded-md bg-surface p-2 shadow-card">
      <Link href={seriesPath(series.slug)} className="group relative block">
        <Cover image={series.cover} title={series.title} eager={eager} />
        <span className="absolute top-1.5 left-1.5">
          <Badge tone="primary">{label(series.type)}</Badge>
        </span>
      </Link>
      <h3 className="line-clamp-2 text-sm font-bold">
        <Link href={seriesPath(series.slug)} className="hover:text-primary-text">
          {series.title}
        </Link>
      </h3>
      {series.latestChapters.length > 0 && (
        <ul className="flex flex-col gap-1 text-xs">
          {series.latestChapters.slice(0, 3).map((chapter) => (
            <li key={chapter.number} className="flex items-center justify-between gap-2">
              <Link
                href={chapterPath(series.slug, chapter.number)}
                className="truncate rounded bg-raised px-1.5 py-0.5 font-medium hover:text-primary-text"
              >
                Ch. {chapter.number}
              </Link>
              {isNew(chapter.publishedAt) ? (
                <Badge tone="link">NEW</Badge>
              ) : (
                <span className="shrink-0 text-muted">{relativeTime(chapter.publishedAt)}</span>
              )}
            </li>
          ))}
        </ul>
      )}
    </article>
  );
}
