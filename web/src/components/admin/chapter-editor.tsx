"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { type ComponentProps, type FormEvent, useEffect, useRef, useState } from "react";
import { Button, Notice, TextAreaField, TextField } from "@/components/auth/form";
import { Badge } from "@/components/badge";
import { Skeleton } from "@/components/skeleton";
import {
  ACCEPTED_IMAGES,
  type AdminChapter,
  type AdminPage,
  admin,
  fromLocalInput,
  MAX_UPLOAD_BYTES,
  move,
  toLocalInput,
} from "@/lib/admin/client";
import { errorMessage } from "@/lib/auth/client";
import { calendarDate } from "@/lib/format";
import { chapterPath } from "@/lib/routes";
import { releaseLabel, Section, useAction } from "./ui";

/** One chapter: its number and title, its pages or text, and its release. */
export function ChapterEditor({ id }: { id: string }) {
  const [chapter, setChapter] = useState<AdminChapter | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    admin
      .chapter(id)
      .then(setChapter)
      .catch((caught) => setError(errorMessage(caught)));
  }, [id]);

  if (error) return <Notice kind="error">{error}</Notice>;
  if (!chapter) {
    return (
      <>
        <p role="status" className="sr-only">
          Loading the chapter…
        </p>
        <Skeleton className="h-96 w-full rounded-md" />
      </>
    );
  }
  return (
    <>
      <p>
        <Link href={`/admin/series/${chapter.seriesId}`} className="text-link underline">
          {chapter.seriesTitle}
        </Link>
      </p>
      <ReleaseSection chapter={chapter} onChange={setChapter} />
      <DetailsSection chapter={chapter} onChange={setChapter} />
      {chapter.seriesType === "MANHWA" ? (
        <PagesSection chapter={chapter} onChange={setChapter} />
      ) : (
        <TextSection chapter={chapter} onChange={setChapter} />
      )}
    </>
  );
}

type SectionProps = { chapter: AdminChapter; onChange: (chapter: AdminChapter) => void };

function DetailsSection({ chapter, onChange }: SectionProps) {
  const [number, setNumber] = useState(chapter.number);
  const [title, setTitle] = useState(chapter.title ?? "");
  const { busy, run, notice } = useAction();

  function submit(event: FormEvent) {
    event.preventDefault();
    void run(async () => {
      onChange(
        await admin.updateChapter(chapter.id, {
          number: number.trim(),
          title: title.trim() || null,
        }),
      );
      return "Saved.";
    });
  }

  return (
    <Section title="Details">
      <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
        {notice}
        <div className="flex flex-wrap gap-3">
          <div className="w-28">
            <TextField
              id="number"
              label="Number"
              inputMode="decimal"
              maxLength={9}
              value={number}
              disabled={chapter.live}
              onChange={(event) => setNumber(event.target.value)}
            />
          </div>
          <div className="min-w-48 flex-1">
            <TextField
              id="chapter-title"
              label="Title (optional)"
              maxLength={300}
              value={title}
              onChange={(event) => setTitle(event.target.value)}
            />
          </div>
        </div>
        {chapter.live ? (
          <p className="text-sm text-muted">
            The number is part of the chapter's address, so it stays once readers can see it.
          </p>
        ) : null}
        <Button busy={busy} className="self-start">
          Save details
        </Button>
      </form>
    </Section>
  );
}

type Upload = {
  name: string;
  status: "waiting" | "uploading" | "done" | "failed";
  error?: string;
};

/** Manhwa pages: uploaded one at a time (bounded memory, and each request stays under the edge's size limit). */
function PagesSection({ chapter, onChange }: SectionProps) {
  const [pages, setPages] = useState<AdminPage[]>(chapter.pages);
  const [uploads, setUploads] = useState<Upload[]>([]);
  const [dirty, setDirty] = useState(false);
  const input = useRef<HTMLInputElement>(null);
  const { busy, run, notice } = useAction();
  const uploading = uploads.some((u) => u.status === "waiting" || u.status === "uploading");

  async function add(files: FileList | null) {
    if (!files || files.length === 0) return;
    // Pickers don't promise an order; sort by name so page-2 comes before page-10.
    const queue = [...files].sort((a, b) =>
      a.name.localeCompare(b.name, undefined, { numeric: true }),
    );
    setUploads(queue.map((file) => ({ name: file.name, status: "waiting" })));
    const update = (index: number, upload: Partial<Upload>) =>
      setUploads((current) => current.map((u, i) => (i === index ? { ...u, ...upload } : u)));
    for (const [index, file] of queue.entries()) {
      if (file.size > MAX_UPLOAD_BYTES) {
        update(index, { status: "failed", error: "The image is over 20 MB." });
        continue;
      }
      update(index, { status: "uploading" });
      try {
        const image = await admin.upload("pages", file);
        setPages((current) => [
          ...current,
          { assetId: image.id, url: image.url, width: image.width, height: image.height },
        ]);
        setDirty(true);
        update(index, { status: "done" });
      } catch (caught) {
        update(index, { status: "failed", error: errorMessage(caught) });
      }
    }
    setUploads((current) => current.filter((u) => u.status === "failed"));
    if (input.current) input.current.value = "";
  }

  function reorder(from: number, to: number) {
    setPages((current) => move(current, from, to));
    setDirty(true);
  }

  function remove(index: number) {
    setPages((current) => current.filter((_, i) => i !== index));
    setDirty(true);
  }

  function save() {
    void run(async () => {
      const saved = await admin.setPages(
        chapter.id,
        pages.map((page) => page.assetId),
      );
      onChange(saved);
      setPages(saved.pages);
      setDirty(false);
      return "Pages saved.";
    });
  }

  const finished = uploads.filter((u) => u.status === "done" || u.status === "failed").length;
  return (
    <Section title="Pages">
      {notice}
      <div className="flex flex-col gap-1">
        <label htmlFor="page-files" className="text-sm font-semibold">
          Add pages
        </label>
        <input
          ref={input}
          id="page-files"
          type="file"
          multiple
          accept={ACCEPTED_IMAGES}
          aria-describedby="page-files-hint"
          disabled={uploading}
          onChange={(event) => void add(event.target.files)}
          className="text-sm"
        />
        <p id="page-files-hint" className="text-sm text-muted">
          PNG, JPEG or WebP, up to 20 MB each, added in file-name order. Pages are resized to 1080
          px wide.
        </p>
      </div>
      <p role="status" className="text-sm empty:hidden">
        {uploading
          ? `Uploading ${Math.min(finished + 1, uploads.length)} of ${uploads.length}…`
          : ""}
      </p>
      {uploads
        .filter((u) => u.status === "failed")
        .map((u) => (
          <Notice key={u.name} kind="error">
            {u.name}: {u.error}
          </Notice>
        ))}
      {pages.length === 0 ? (
        <p className="text-muted">No pages yet.</p>
      ) : (
        <ol className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          {pages.map((page, index) => (
            // biome-ignore lint/suspicious/noArrayIndexKey: an image may be used twice; thumbnails hold no state
            <li key={`${page.assetId}-${index}`} className="flex flex-col gap-1">
              {page.url ? (
                <img
                  src={page.url}
                  width={page.width}
                  height={page.height}
                  alt={`Page ${index + 1}`}
                  loading="lazy"
                  className="aspect-[2/3] w-full rounded bg-raised object-cover object-top"
                />
              ) : (
                <div className="aspect-[2/3] w-full rounded bg-raised" />
              )}
              <div className="flex items-center justify-between gap-1 text-sm">
                <span>{index + 1}</span>
                <span className="flex gap-1">
                  <IconButton
                    label={`Move page ${index + 1} earlier`}
                    disabled={index === 0}
                    onClick={() => reorder(index, index - 1)}
                  >
                    ↑
                  </IconButton>
                  <IconButton
                    label={`Move page ${index + 1} later`}
                    disabled={index === pages.length - 1}
                    onClick={() => reorder(index, index + 1)}
                  >
                    ↓
                  </IconButton>
                  <IconButton label={`Remove page ${index + 1}`} onClick={() => remove(index)}>
                    ×
                  </IconButton>
                </span>
              </div>
            </li>
          ))}
        </ol>
      )}
      <div className="flex items-center gap-3">
        <Button
          type="button"
          busy={busy}
          disabled={!dirty || uploading}
          onClick={save}
          className="self-start"
        >
          Save pages
        </Button>
        {dirty ? <span className="text-sm text-muted">Unsaved changes</span> : null}
      </div>
    </Section>
  );
}

function IconButton({
  label,
  children,
  ...button
}: { label: string; children: string } & ComponentProps<"button">) {
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      className="inline-flex size-8 items-center justify-center rounded border border-line bg-surface hover:bg-raised disabled:opacity-40"
      {...button}
    >
      <span aria-hidden="true">{children}</span>
    </button>
  );
}

function TextSection({ chapter, onChange }: SectionProps) {
  const [text, setText] = useState(chapter.body ?? "");
  const { busy, run, notice } = useAction();

  function submit(event: FormEvent) {
    event.preventDefault();
    void run(async () => {
      onChange(await admin.setBody(chapter.id, text));
      return "Text saved.";
    });
  }

  return (
    <Section title="Text">
      <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
        {notice}
        <TextAreaField
          id="body"
          label="Chapter text"
          rows={20}
          maxLength={200_000}
          value={text}
          onChange={(event) => setText(event.target.value)}
          hint="Markdown: blank lines between paragraphs, *italic* and **bold**."
          className="font-serif"
        />
        <Button busy={busy} className="self-start">
          Save text
        </Button>
      </form>
    </Section>
  );
}

function ReleaseSection({ chapter, onChange }: SectionProps) {
  const router = useRouter();
  const [when, setWhen] = useState(chapter.publishAt ? toLocalInput(chapter.publishAt) : "");
  const { busy, run, notice } = useAction();

  function schedule(event: FormEvent) {
    event.preventDefault();
    const at = fromLocalInput(when);
    if (!at) return;
    void run(async () => {
      onChange(await admin.schedule(chapter.id, at));
      return "Scheduled.";
    });
  }

  function releaseNow() {
    void run(async () => {
      onChange(await admin.schedule(chapter.id));
      return "Released. Readers can see it now.";
    });
  }

  function unschedule() {
    void run(async () => {
      onChange(await admin.unschedule(chapter.id));
      return "Back to draft.";
    });
  }

  function remove() {
    if (!window.confirm(`Delete chapter ${chapter.number}? This can't be undone.`)) return;
    void run(async () => {
      await admin.deleteChapter(chapter.id);
      router.replace(`/admin/series/${chapter.seriesId}`);
      return undefined;
    });
  }

  return (
    <Section
      title="Release"
      actions={<Badge tone={chapter.live ? "success" : "neutral"}>{releaseLabel(chapter)}</Badge>}
    >
      {notice}
      {chapter.live ? (
        <p>
          Readers can see this chapter since{" "}
          <strong>{calendarDate(chapter.publishedAt ?? chapter.publishAt ?? "")}</strong>.{" "}
          <Link
            href={chapterPath(chapter.seriesSlug, chapter.number)}
            className="text-link underline"
          >
            Open in the reader
          </Link>
        </p>
      ) : (
        <>
          <p className="text-sm text-muted">
            {chapter.status === "SCHEDULED" && chapter.publishAt
              ? `Goes live on ${new Date(chapter.publishAt).toLocaleString()} (your time).`
              : "A draft: only staff can see it."}
          </p>
          <form onSubmit={schedule} className="flex flex-wrap items-end gap-3" noValidate>
            <TextField
              id="publish-at"
              label="Release at (your time)"
              type="datetime-local"
              value={when}
              onChange={(event) => setWhen(event.target.value)}
            />
            <Button busy={busy} variant="secondary" disabled={!when}>
              Schedule
            </Button>
            <Button type="button" busy={busy} onClick={releaseNow}>
              Release now
            </Button>
          </form>
          <div className="flex flex-wrap gap-3">
            {chapter.status === "SCHEDULED" ? (
              <Button type="button" variant="secondary" busy={busy} onClick={unschedule}>
                Back to draft
              </Button>
            ) : null}
            <Button type="button" variant="danger" busy={busy} onClick={remove}>
              Delete chapter
            </Button>
          </div>
        </>
      )}
    </Section>
  );
}
