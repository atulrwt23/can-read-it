"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { Button, Notice } from "@/components/auth/form";
import { Skeleton } from "@/components/skeleton";
import {
  admin,
  type ModerationAction,
  type ModerationQueue,
  type QueueItem,
} from "@/lib/admin/client";
import { errorMessage } from "@/lib/auth/client";
import { relativeTime } from "@/lib/format";
import { chapterPath } from "@/lib/routes";
import { Section, useAction } from "./ui";

const PAGE_SIZE = 20;

const REASONS: Record<string, string> = {
  SPAM: "Spam",
  HARASSMENT: "Harassment",
  SPOILER: "Spoiler",
  OFF_TOPIC: "Off topic",
  OTHER: "Other",
};

const ACTIONS: Record<string, string> = {
  REMOVE_COMMENT: "Removed a comment",
  KEEP_COMMENT: "Kept a comment",
};

/** Reported comments, the one waiting longest first, and the latest decisions (docs/modules/moderation.md). */
export function ModerationView() {
  const [page, setPage] = useState(0);
  const [queue, setQueue] = useState<ModerationQueue | null>(null);
  const [actions, setActions] = useState<ModerationAction[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const { busy, run, notice } = useAction();

  const load = useCallback(async (index: number) => {
    setError(null);
    try {
      const [loaded, recent] = await Promise.all([
        admin.moderationQueue(index, PAGE_SIZE),
        admin.moderationActions(),
      ]);
      setQueue(loaded);
      setActions(recent);
    } catch (caught) {
      setError(errorMessage(caught));
    }
  }, []);

  useEffect(() => {
    void load(page);
  }, [load, page]);

  function decide(item: QueueItem, remove: boolean, note: string) {
    void run(async () => {
      await (remove
        ? admin.removeComment(item.commentId, note)
        : admin.keepComment(item.commentId, note));
      await load(page);
      return remove ? "The comment was removed." : "The comment was kept and its reports closed.";
    });
  }

  const pages = queue ? Math.max(1, Math.ceil(queue.total / PAGE_SIZE)) : 1;

  return (
    <>
      {error ? <Notice kind="error">{error}</Notice> : null}
      {notice}
      <Section title={queue ? `Reported comments (${queue.total})` : "Reported comments"}>
        {queue === null ? (
          <Skeleton className="h-32 w-full" />
        ) : queue.items.length === 0 ? (
          <p className="text-muted">Nothing to review. Reported comments show up here.</p>
        ) : (
          <ul className="flex flex-col gap-4">
            {queue.items.map((item) => (
              <QueueCard key={item.commentId} item={item} busy={busy} onDecide={decide} />
            ))}
          </ul>
        )}
        {queue && pages > 1 ? (
          <div className="flex items-center gap-3 text-sm">
            <Button
              type="button"
              variant="secondary"
              disabled={page === 0}
              onClick={() => setPage(page - 1)}
            >
              Previous
            </Button>
            <span>
              Page {page + 1} of {pages}
            </span>
            <Button
              type="button"
              variant="secondary"
              disabled={page + 1 >= pages}
              onClick={() => setPage(page + 1)}
            >
              Next
            </Button>
          </div>
        ) : null}
      </Section>
      <Section title="Recent decisions">
        {actions === null ? (
          <Skeleton className="h-16 w-full" />
        ) : actions.length === 0 ? (
          <p className="text-muted">No decisions yet.</p>
        ) : (
          <ul className="flex flex-col divide-y divide-line text-sm">
            {actions.map((action) => (
              <li key={action.id} className="flex flex-col gap-0.5 py-2">
                <span>
                  <span className="font-semibold">
                    {action.moderator ? `@${action.moderator.handle}` : "A deleted account"}
                  </span>{" "}
                  {(ACTIONS[action.action] ?? action.action).toLowerCase()}{" "}
                  <time dateTime={action.at} className="text-muted">
                    {relativeTime(action.at)}
                  </time>
                </span>
                {action.note ? <span className="text-muted">“{action.note}”</span> : null}
              </li>
            ))}
          </ul>
        )}
      </Section>
    </>
  );
}

function QueueCard({
  item,
  busy,
  onDecide,
}: {
  item: QueueItem;
  busy: boolean;
  onDecide(item: QueueItem, remove: boolean, note: string): void;
}) {
  const [note, setNote] = useState("");
  const { seriesSlug, seriesTitle, chapterNumber } = item.place;
  const noteId = `note-${item.commentId}`;
  return (
    <li className="flex flex-col gap-3 rounded border border-line p-4">
      <div className="flex flex-wrap items-baseline justify-between gap-2 text-sm">
        <span>
          {item.author ? (
            <>
              <span className="font-semibold">{item.author.displayName}</span>{" "}
              <span className="text-muted">@{item.author.handle}</span>
            </>
          ) : (
            <span className="text-muted">A deleted account</span>
          )}
          {" · "}
          {seriesSlug && chapterNumber ? (
            <Link href={chapterPath(seriesSlug, chapterNumber)} className="text-link underline">
              {seriesTitle}, Ch. {chapterNumber}
            </Link>
          ) : (
            <span className="text-muted">a chapter that is no longer readable</span>
          )}
        </span>
        <span className="text-muted">
          {item.reports} {item.reports === 1 ? "report" : "reports"}, first{" "}
          {relativeTime(item.firstReportedAt)}
        </span>
      </div>
      <blockquote className="border-l-4 border-line pl-3 break-words whitespace-pre-line">
        {item.body}
      </blockquote>
      <ul className="flex flex-wrap gap-2 text-sm" aria-label="Reasons">
        {Object.entries(item.reasons).map(([reason, count]) => (
          <li key={reason} className="rounded bg-raised px-2 py-0.5">
            {REASONS[reason] ?? reason} × {count}
          </li>
        ))}
      </ul>
      {item.notes.length > 0 ? (
        <ul className="flex flex-col gap-1 text-sm text-muted" aria-label="Notes from readers">
          {item.notes.map((text, index) => (
            // biome-ignore lint/suspicious/noArrayIndexKey: notes have no ID and never reorder
            <li key={index}>“{text}”</li>
          ))}
        </ul>
      ) : null}
      <div className="flex flex-col gap-1">
        <label htmlFor={noteId} className="text-sm font-semibold">
          Note for the log (optional)
        </label>
        <input
          id={noteId}
          value={note}
          maxLength={500}
          onChange={(event) => setNote(event.target.value)}
          className="h-10 rounded border border-line bg-surface px-3 text-base focus:border-primary-text focus:outline-none"
        />
      </div>
      <div className="flex flex-wrap gap-3">
        <Button
          type="button"
          variant="danger"
          busy={busy}
          onClick={() => onDecide(item, true, note)}
        >
          Remove comment
        </Button>
        <Button
          type="button"
          variant="secondary"
          busy={busy}
          onClick={() => onDecide(item, false, note)}
        >
          Keep comment
        </Button>
      </div>
    </li>
  );
}
