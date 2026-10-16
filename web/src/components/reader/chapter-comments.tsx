"use client";

import Link from "next/link";
import { type FormEvent, useCallback, useEffect, useRef, useState } from "react";
import { SessionProvider, useSession } from "@/components/auth/session";
import {
  type CommentView,
  comments as commentsApi,
  errorMessage,
  type ReportReason,
} from "@/lib/auth/client";
import { relativeTime } from "@/lib/format";
import { chapterPath } from "@/lib/routes";

const MAX_LENGTH = 2000;
const PAGE_SIZE = 20;

type Page = { items: CommentView[]; nextCursor?: string | null };

/**
 * Comments under a chapter (docs/modules/engagement.md). They load when the reader gets near the end of the
 * chapter, from the public, cacheable list; writing needs an account with a confirmed email. The reader has no site
 * layout, so the section brings its own session.
 */
export function ChapterComments(props: { slug: string; number: string }) {
  return (
    <SessionProvider>
      <Comments {...props} />
    </SessionProvider>
  );
}

function Comments({ slug, number }: { slug: string; number: string }) {
  const session = useSession();
  const sectionRef = useRef<HTMLElement>(null);
  const [near, setNear] = useState(false);
  const [items, setItems] = useState<CommentView[] | null>(null);
  const [cursor, setCursor] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [failed, setFailed] = useState(false);
  const [message, setMessage] = useState<{ kind: "error" | "info"; text: string } | null>(null);

  // Start loading a little before the section scrolls into view.
  useEffect(() => {
    const section = sectionRef.current;
    if (!section || typeof IntersectionObserver === "undefined") {
      setNear(true);
      return;
    }
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          setNear(true);
          observer.disconnect();
        }
      },
      { rootMargin: "800px 0px" },
    );
    observer.observe(section);
    return () => observer.disconnect();
  }, []);

  const load = useCallback(
    async (after: string | null) => {
      setLoading(true);
      setFailed(false);
      try {
        const query = new URLSearchParams({ limit: String(PAGE_SIZE) });
        if (after) query.set("cursor", after);
        // Public and the same for everyone, so no cookies. "no-cache" skips the browser's stale copy (the
        // response allows stale-while-revalidate for shared caches), so a reader's new comment survives a reload.
        const response = await fetch(
          `/api/v1/series/${slug}/chapters/${encodeURIComponent(number)}/comments?${query}`,
          { credentials: "omit", cache: "no-cache", headers: { Accept: "application/json" } },
        );
        if (!response.ok) throw new Error(`comments answered ${response.status}`);
        const page = (await response.json()) as Page;
        setItems((current) => (after && current ? [...current, ...page.items] : page.items));
        setCursor(page.nextCursor ?? null);
      } catch {
        setFailed(true);
      } finally {
        setLoading(false);
      }
    },
    [slug, number],
  );

  useEffect(() => {
    if (near) void load(null);
  }, [near, load]);

  const myHandle = session.status === "signed-in" ? session.me.handle : null;

  async function remove(comment: CommentView) {
    setMessage(null);
    try {
      await commentsApi.remove(comment.id);
      setItems((current) => current?.filter((item) => item.id !== comment.id) ?? null);
      setMessage({ kind: "info", text: "Your comment was deleted." });
    } catch (caught) {
      setMessage({ kind: "error", text: errorMessage(caught) });
    }
  }

  return (
    <section
      ref={sectionRef}
      aria-labelledby="comments-title"
      className="mx-auto mb-16 flex max-w-2xl flex-col gap-4 px-4"
    >
      <h2 id="comments-title" className="text-xl">
        Comments
      </h2>
      {session.status === "signed-out" ? (
        <p className="text-sm">
          <Link
            href={`/login?next=${encodeURIComponent(chapterPath(slug, number))}`}
            className="text-link underline"
          >
            Sign in to comment
          </Link>
        </p>
      ) : session.status === "signed-in" && !session.me.emailVerified ? (
        <p className="text-sm text-muted">
          <Link href="/account" className="text-link underline">
            Confirm your email
          </Link>{" "}
          to comment.
        </p>
      ) : session.status === "signed-in" ? (
        <CommentForm
          slug={slug}
          number={number}
          onPosted={(comment) => {
            setItems((current) => [comment, ...(current ?? [])]);
            setMessage({ kind: "info", text: "Your comment was posted." });
          }}
        />
      ) : null}
      {message ? (
        <p role={message.kind === "error" ? "alert" : "status"} className="text-sm text-muted">
          {message.text}
        </p>
      ) : null}
      {failed ? (
        <p role="alert" className="text-sm text-muted">
          Comments could not be loaded.{" "}
          <button
            type="button"
            className="text-link underline"
            onClick={() => void load(items ? cursor : null)}
          >
            Try again
          </button>
        </p>
      ) : null}
      {items === null ? (
        failed ? null : (
          <p className="text-sm text-muted">Loading comments…</p>
        )
      ) : items.length === 0 ? (
        <p className="text-sm text-muted">No comments yet. Be the first.</p>
      ) : (
        <ul className="flex flex-col divide-y divide-line rounded-md bg-surface px-4 shadow-card">
          {items.map((comment) => (
            <li key={comment.id} className="flex flex-col gap-1 py-3">
              <p className="flex flex-wrap items-baseline gap-x-2 text-sm">
                <span className="font-semibold">{comment.author.displayName}</span>
                {comment.author.displayName === comment.author.handle ? null : (
                  <span className="text-muted">@{comment.author.handle}</span>
                )}
                <time dateTime={comment.createdAt} className="text-muted">
                  {relativeTime(comment.createdAt)}
                </time>
              </p>
              <p className="break-words whitespace-pre-line">{comment.body}</p>
              {comment.author.handle === myHandle ? (
                <button
                  type="button"
                  onClick={() => void remove(comment)}
                  className="self-start text-sm text-link underline"
                >
                  Delete<span className="sr-only"> your comment</span>
                </button>
              ) : myHandle ? (
                <ReportComment comment={comment} />
              ) : null}
            </li>
          ))}
        </ul>
      )}
      {cursor && !failed ? (
        <button
          type="button"
          disabled={loading}
          onClick={() => void load(cursor)}
          className="self-center rounded border border-line bg-surface px-4 py-2 text-sm font-semibold hover:bg-raised disabled:opacity-60"
        >
          {loading ? "Loading…" : "Load more comments"}
        </button>
      ) : null}
    </section>
  );
}

function CommentForm({
  slug,
  number,
  onPosted,
}: {
  slug: string;
  number: string;
  onPosted(comment: CommentView): void;
}) {
  const [body, setBody] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const tooLong = body.length > MAX_LENGTH;

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!body.trim() || tooLong) return;
    setBusy(true);
    setError(null);
    try {
      onPosted(await commentsApi.post(slug, number, body));
      setBody("");
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={(event) => void submit(event)} className="flex flex-col gap-2">
      <label htmlFor="comment-body" className="text-sm font-semibold">
        Add a comment
      </label>
      <textarea
        id="comment-body"
        value={body}
        onChange={(event) => setBody(event.target.value)}
        rows={3}
        aria-describedby="comment-hint"
        aria-invalid={tooLong || undefined}
        className="rounded border border-line bg-surface px-3 py-2 text-base focus:border-primary-text focus:outline-none"
      />
      <div className="flex items-center justify-between gap-3">
        <p id="comment-hint" className="text-sm text-muted">
          {body.length} / {MAX_LENGTH} characters. Be kind, and keep spoilers vague.
        </p>
        <button
          type="submit"
          disabled={busy || !body.trim() || tooLong}
          aria-busy={busy || undefined}
          className="h-10 rounded bg-primary px-4 text-sm font-semibold text-on-primary hover:brightness-95 disabled:cursor-not-allowed disabled:opacity-60"
        >
          Post comment
        </button>
      </div>
      {error ? (
        <p role="alert" className="text-sm font-semibold">
          {error}
        </p>
      ) : null}
    </form>
  );
}

const REASONS: { value: ReportReason; label: string }[] = [
  { value: "SPAM", label: "Spam or advertising" },
  { value: "HARASSMENT", label: "Harassment or hate" },
  { value: "SPOILER", label: "Unmarked spoilers" },
  { value: "OFF_TOPIC", label: "Off topic" },
  { value: "OTHER", label: "Something else" },
];

/** Sends a comment to the moderators, with a reason and an optional note. Reporters stay anonymous to them. */
function ReportComment({ comment }: { comment: CommentView }) {
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState<ReportReason>("SPAM");
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const ids = `report-${comment.id}`;

  if (sent) {
    return (
      <p role="status" className="text-sm text-muted">
        Thanks. A moderator will take a look.
      </p>
    );
  }
  if (!open) {
    return (
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="self-start text-sm text-muted underline"
      >
        Report<span className="sr-only"> comment by {comment.author.displayName}</span>
      </button>
    );
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await commentsApi.report(comment.id, reason, note.trim());
      setSent(true);
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setBusy(false);
    }
  }

  return (
    <form
      onSubmit={(event) => void submit(event)}
      aria-label={`Report comment by ${comment.author.displayName}`}
      className="flex flex-col gap-2 rounded border border-line p-3"
    >
      <div className="flex flex-col gap-1">
        <label htmlFor={`${ids}-reason`} className="text-sm font-semibold">
          Why are you reporting it?
        </label>
        <select
          id={`${ids}-reason`}
          value={reason}
          onChange={(event) => setReason(event.target.value as ReportReason)}
          className="h-10 rounded border border-line bg-surface px-2 text-base"
        >
          {REASONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      </div>
      <div className="flex flex-col gap-1">
        <label htmlFor={`${ids}-note`} className="text-sm font-semibold">
          Anything the moderators should know? (optional)
        </label>
        <input
          id={`${ids}-note`}
          value={note}
          maxLength={500}
          onChange={(event) => setNote(event.target.value)}
          className="h-10 rounded border border-line bg-surface px-3 text-base focus:border-primary-text focus:outline-none"
        />
      </div>
      {error ? (
        <p role="alert" className="text-sm font-semibold">
          {error}
        </p>
      ) : null}
      <div className="flex gap-3">
        <button
          type="submit"
          disabled={busy}
          aria-busy={busy || undefined}
          className="h-9 rounded bg-primary px-3 text-sm font-semibold text-on-primary disabled:opacity-60"
        >
          Send report
        </button>
        <button
          type="button"
          onClick={() => setOpen(false)}
          className="h-9 rounded border border-line px-3 text-sm font-semibold hover:bg-raised"
        >
          Cancel
        </button>
      </div>
    </form>
  );
}
