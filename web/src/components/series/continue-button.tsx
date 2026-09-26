"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { getProgress, type Progress } from "@/lib/history";
import { chapterPath } from "@/lib/routes";

/** Primary action: continue from guest history when there is some, otherwise start at chapter one. */
export function ContinueButton({
  slug,
  first,
}: {
  slug: string;
  first: string | null | undefined;
}) {
  const [progress, setProgress] = useState<Progress | undefined>();

  useEffect(() => setProgress(getProgress(slug)), [slug]);

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
