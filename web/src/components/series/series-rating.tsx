"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useSession } from "@/components/auth/session";
import type { SeriesRating as Rating } from "@/lib/api/types";
import { errorMessage, ratings } from "@/lib/auth/client";
import { compactNumber } from "@/lib/format";
import { seriesPath } from "@/lib/routes";

const SCORES = [1, 2, 3, 4, 5] as const;

/**
 * The series' average (rendered on the server, the same for everyone) and, after hydration, the reader's own score
 * as five stars. Rating needs an account with a confirmed email (docs/modules/engagement.md).
 */
export function SeriesRating({ slug, initial }: { slug: string; initial: Rating | null }) {
  const session = useSession();
  const [summary, setSummary] = useState<Rating | null>(initial);
  const [score, setScore] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ kind: "error" | "info"; text: string } | null>(null);
  const signedInId = session.status === "signed-in" ? session.me.id : null;

  useEffect(() => {
    if (!signedInId) return;
    let current = true;
    ratings
      .mine(slug)
      .then((mine) => {
        if (!current) return;
        setScore(mine.score ?? null);
        setSummary(mine.series);
      })
      .catch(() => undefined);
    return () => {
      current = false;
    };
  }, [slug, signedInId]);

  async function save(next: number | null) {
    const previous = score;
    setScore(next);
    setBusy(true);
    setMessage(null);
    try {
      const result = next === null ? await ratings.remove(slug) : await ratings.rate(slug, next);
      setScore(result.score ?? null);
      setSummary(result.series);
      setMessage({
        kind: "info",
        text: next === null ? "Your rating was removed." : `You rated it ${next} out of 5.`,
      });
    } catch (caught) {
      setScore(previous);
      setMessage({ kind: "error", text: errorMessage(caught) });
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex flex-col gap-2">
      <Average summary={summary} />
      {session.status === "signed-out" ? (
        <p className="text-sm">
          <Link
            href={`/login?next=${encodeURIComponent(seriesPath(slug))}`}
            className="text-link underline"
          >
            Sign in to rate this series
          </Link>
        </p>
      ) : session.status === "signed-in" && !session.me.emailVerified ? (
        <p className="text-sm text-muted">
          <Link href="/account" className="text-link underline">
            Confirm your email
          </Link>{" "}
          to rate this series.
        </p>
      ) : session.status === "signed-in" ? (
        <div className="flex flex-wrap items-center gap-3">
          <div
            role="radiogroup"
            aria-labelledby={`rate-${slug}`}
            className="flex items-center gap-2"
          >
            <span id={`rate-${slug}`} className="text-sm text-muted">
              Your rating
            </span>
            <span className="flex">
              {SCORES.map((value) => (
                <label key={value} className="cursor-pointer p-0.5">
                  <input
                    type="radio"
                    name={`rating-${slug}`}
                    value={value}
                    checked={score === value}
                    disabled={busy}
                    onChange={() => void save(value)}
                    className="peer sr-only"
                  />
                  <span className="sr-only">
                    {value} {value === 1 ? "star" : "stars"}
                  </span>
                  <Star
                    filled={score !== null && value <= score}
                    className="size-6 rounded-sm text-star peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-primary-text"
                  />
                </label>
              ))}
            </span>
          </div>
          {score !== null ? (
            <button
              type="button"
              disabled={busy}
              onClick={() => void save(null)}
              className="text-sm text-link underline disabled:opacity-60"
            >
              Remove rating
            </button>
          ) : null}
        </div>
      ) : null}
      {message ? (
        <p role={message.kind === "error" ? "alert" : "status"} className="text-sm text-muted">
          {message.text}
        </p>
      ) : null}
    </div>
  );
}

function Average({ summary }: { summary: Rating | null }) {
  if (!summary) return null;
  if (summary.count === 0 || summary.average == null) {
    return <p className="text-sm text-muted">No ratings yet</p>;
  }
  const count = summary.count === 1 ? "1 rating" : `${compactNumber(summary.count)} ratings`;
  return (
    <p className="flex items-center gap-1.5 text-sm">
      <Star filled className="size-4 text-star" />
      <span className="font-semibold">
        {summary.average.toFixed(1)}
        <span className="sr-only"> out of 5</span>
      </span>
      <span className="text-muted">({count})</span>
    </p>
  );
}

function Star({ filled, className }: { filled: boolean; className: string }) {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 24 24"
      className={className}
      fill={filled ? "currentColor" : "none"}
      stroke="currentColor"
      strokeWidth="1.75"
      strokeLinejoin="round"
    >
      <path d="m12 3 2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1-4.4-4.3 6.1-.9Z" />
    </svg>
  );
}
