"use client";

import Link from "next/link";
import { useState } from "react";
import { errorMessage } from "@/lib/auth/client";
import { setFollowing } from "@/lib/reading/store";
import { useSeriesReading } from "@/lib/reading/use-series-reading";
import { seriesPath } from "@/lib/routes";

const STYLE =
  "inline-flex items-center gap-2 rounded border border-line bg-surface px-4 py-2 text-sm font-semibold hover:bg-raised aria-pressed:border-primary-text aria-pressed:text-primary-text disabled:opacity-60";

/**
 * Follows the series into the reader's library. Guests are sent to sign in first. Pages are the same for every
 * visitor, so the button knows its state only after hydration.
 */
export function FollowButton({ slug }: { slug: string }) {
  const [reading, setReading] = useSeriesReading(slug);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (reading && !reading.signedIn) {
    return (
      <Link href={`/login?next=${encodeURIComponent(seriesPath(slug))}`} className={STYLE}>
        <Plus /> Follow
      </Link>
    );
  }

  const following = reading?.following ?? false;

  async function toggle() {
    if (!reading) return;
    setBusy(true);
    setError(null);
    try {
      await setFollowing(slug, !following);
      setReading({ ...reading, following: !following });
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setBusy(false);
    }
  }

  return (
    <span className="inline-flex flex-col gap-1">
      <button
        type="button"
        aria-pressed={following}
        disabled={!reading || busy}
        onClick={() => void toggle()}
        className={STYLE}
      >
        {following ? <Check /> : <Plus />}
        {following ? "Following" : "Follow"}
      </button>
      {error ? (
        <span role="alert" className="text-sm text-muted">
          {error}
        </span>
      ) : null}
    </span>
  );
}

function Plus() {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 24 24"
      className="size-4"
      fill="none"
      stroke="currentColor"
      strokeWidth="2.5"
    >
      <path d="M12 5v14M5 12h14" />
    </svg>
  );
}

function Check() {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 24 24"
      className="size-4"
      fill="none"
      stroke="currentColor"
      strokeWidth="2.5"
    >
      <path d="m5 12 5 5L20 7" />
    </svg>
  );
}
