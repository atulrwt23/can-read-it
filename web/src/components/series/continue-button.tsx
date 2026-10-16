"use client";

import Link from "next/link";
import { useSeriesReading } from "@/lib/reading/use-series-reading";
import { chapterPath } from "@/lib/routes";

/** Primary action: continue where the reader left off (account or guest history), otherwise start at chapter one. */
export function ContinueButton({
  slug,
  first,
}: {
  slug: string;
  first: string | null | undefined;
}) {
  const [reading] = useSeriesReading(slug);
  const progress = reading?.progress;

  const className =
    "inline-block rounded bg-primary px-4 py-2 text-sm font-semibold text-on-primary hover:brightness-95";
  if (progress) {
    return (
      <Link href={chapterPath(slug, progress.number)} className={className}>
        Continue Ch. {progress.number}
      </Link>
    );
  }
  if (!first) {
    return <span className="text-sm text-muted">No chapters yet</span>;
  }
  return (
    <Link href={chapterPath(slug, first)} className={className}>
      Start reading
    </Link>
  );
}
