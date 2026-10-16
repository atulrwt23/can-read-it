"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { type FormEvent, useEffect, useState } from "react";
import { Button, Notice, SelectField, TextAreaField, TextField } from "@/components/auth/form";
import { Badge } from "@/components/badge";
import { Skeleton } from "@/components/skeleton";
import {
  ACCEPTED_IMAGES,
  type AdminSeries,
  admin,
  MAX_UPLOAD_BYTES,
  type SeriesForm,
} from "@/lib/admin/client";
import type { Genre } from "@/lib/api/types";
import { api, errorMessage } from "@/lib/auth/client";
import { calendarDate, label } from "@/lib/format";
import { seriesPath } from "@/lib/routes";
import { releaseLabel, Section, useAction } from "./ui";

type Cover = { id: string; url: string; width: number; height: number };

const EMPTY: SeriesForm = {
  title: "",
  type: "MANHWA",
  status: "ONGOING",
  visibility: "DRAFT",
  ageRating: "ALL",
  synopsis: "",
  altTitles: [],
  genres: [],
};

function formOf(series: AdminSeries): SeriesForm {
  return {
    title: series.title,
    slug: series.slug,
    type: series.type,
    status: series.status,
    visibility: series.visibility,
    ageRating: series.ageRating,
    synopsis: series.synopsis,
    altTitles: series.altTitles,
    genres: series.genres,
    coverAssetId: series.coverAssetId ?? null,
    firstReleasedYear: series.firstReleasedYear ?? null,
    releaseCadence: series.releaseCadence ?? null,
  };
}

/** Creates a series (without `id`) or edits one, with its chapter list. */
export function SeriesEditor({ id }: { id?: string }) {
  const [series, setSeries] = useState<AdminSeries | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!id) return;
    admin
      .series(id)
      .then(setSeries)
      .catch((caught) => setError(errorMessage(caught)));
  }, [id]);

  if (error) return <Notice kind="error">{error}</Notice>;
  if (id && !series) {
    return (
      <>
        <p role="status" className="sr-only">
          Loading the series…
        </p>
        <Skeleton className="h-96 w-full rounded-md" />
      </>
    );
  }
  return (
    <>
      <DetailsSection series={series} onSaved={setSeries} />
      {series ? <ChaptersSection series={series} /> : null}
    </>
  );
}

function DetailsSection({
  series,
  onSaved,
}: {
  series: AdminSeries | null;
  onSaved: (series: AdminSeries) => void;
}) {
  const router = useRouter();
  const [form, setForm] = useState<SeriesForm>(series ? formOf(series) : EMPTY);
  const [altTitles, setAltTitles] = useState((series?.altTitles ?? []).join("\n"));
  const [cover, setCover] = useState<Cover | null>(
    series?.cover && series.coverAssetId ? { id: series.coverAssetId, ...series.cover } : null,
  );
  const [genres, setGenres] = useState<Genre[]>([]);
  const [uploading, setUploading] = useState(false);
  const { busy, run, notice, setMessage } = useAction();

  useEffect(() => {
    api<Genre[]>("GET", "/api/v1/genres")
      .then(setGenres)
      .catch(() => setGenres([]));
  }, []);

  const set = <K extends keyof SeriesForm>(key: K, value: SeriesForm[K]) =>
    setForm((current) => ({ ...current, [key]: value }));

  function toggleGenre(slug: string, on: boolean) {
    const current = form.genres ?? [];
    set("genres", on ? [...current, slug] : current.filter((g) => g !== slug));
  }

  async function uploadCover(file: File | undefined) {
    if (!file) return;
    if (file.size > MAX_UPLOAD_BYTES) {
      setMessage({ kind: "error", text: "That image is over 20 MB." });
      return;
    }
    setUploading(true);
    setMessage(null);
    try {
      const image = await admin.upload("covers", file);
      setCover(image);
      set("coverAssetId", image.id);
    } catch (caught) {
      setMessage({ kind: "error", text: errorMessage(caught) });
    } finally {
      setUploading(false);
    }
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    void run(async () => {
      const body: SeriesForm = {
        ...form,
        slug: form.slug?.trim() || null,
        altTitles: altTitles
          .split("\n")
          .map((t) => t.trim())
          .filter(Boolean),
        firstReleasedYear: form.firstReleasedYear || null,
        releaseCadence: form.releaseCadence?.trim() || null,
      };
      if (series) {
        const saved = await admin.updateSeries(series.id, body);
        onSaved(saved);
        setForm(formOf(saved));
        return "Saved.";
      }
      const created = await admin.createSeries(body);
      router.replace(`/admin/series/${created.id}`);
      return undefined;
    });
  }

  return (
    <Section
      title="Details"
      actions={
        series && series.visibility !== "DRAFT" ? (
          <Link href={seriesPath(series.slug)} className="text-sm text-link underline">
            View as a reader
          </Link>
        ) : null
      }
    >
      <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
        {notice}
        <TextField
          id="title"
          label="Title"
          required
          maxLength={300}
          value={form.title}
          onChange={(event) => set("title", event.target.value)}
        />
        <TextField
          id="slug"
          label="URL name"
          maxLength={80}
          value={form.slug ?? ""}
          onChange={(event) => set("slug", event.target.value)}
          hint={
            series
              ? "Lowercase letters, digits and hyphens. The old address keeps redirecting."
              : "Lowercase letters, digits and hyphens. Leave empty to derive it from the title."
          }
        />
        <div className="grid gap-4 sm:grid-cols-2">
          <SelectField
            id="type"
            label="Type"
            value={form.type}
            disabled={series !== null}
            hint={series ? "Fixed once the series exists." : undefined}
            onChange={(event) => set("type", event.target.value as SeriesForm["type"])}
          >
            <option value="MANHWA">Manhwa</option>
            <option value="NOVEL">Novel</option>
          </SelectField>
          <SelectField
            id="visibility"
            label="Visibility"
            value={form.visibility}
            onChange={(event) => set("visibility", event.target.value as SeriesForm["visibility"])}
            hint="Only published series appear in lists and search. Unlisted ones open by link."
          >
            <option value="DRAFT">Draft</option>
            <option value="PUBLISHED">Published</option>
            <option value="UNLISTED">Unlisted</option>
            <option value="REMOVED">Removed</option>
          </SelectField>
          <SelectField
            id="status"
            label="Status"
            value={form.status}
            onChange={(event) => set("status", event.target.value as SeriesForm["status"])}
          >
            <option value="ONGOING">Ongoing</option>
            <option value="COMPLETED">Completed</option>
            <option value="HIATUS">Hiatus</option>
          </SelectField>
          <SelectField
            id="age-rating"
            label="Age rating"
            value={form.ageRating}
            onChange={(event) => set("ageRating", event.target.value as SeriesForm["ageRating"])}
          >
            <option value="ALL">All ages</option>
            <option value="TEEN">Teen</option>
            <option value="MATURE">Mature</option>
          </SelectField>
        </div>
        <TextAreaField
          id="synopsis"
          label="Synopsis"
          rows={5}
          maxLength={5000}
          value={form.synopsis ?? ""}
          onChange={(event) => set("synopsis", event.target.value)}
        />
        <TextAreaField
          id="alt-titles"
          label="Other titles"
          rows={2}
          value={altTitles}
          onChange={(event) => setAltTitles(event.target.value)}
          hint="One per line: original-language or alternative titles, used by search."
        />
        <fieldset className="flex flex-col gap-2">
          <legend className="mb-1 text-sm font-semibold">Genres</legend>
          <div className="flex flex-wrap gap-x-4 gap-y-2">
            {genres.map((genre) => (
              <label key={genre.slug} className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={(form.genres ?? []).includes(genre.slug)}
                  onChange={(event) => toggleGenre(genre.slug, event.target.checked)}
                />
                {genre.name}
              </label>
            ))}
          </div>
        </fieldset>
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField
            id="first-released"
            label="First released (year)"
            inputMode="numeric"
            maxLength={4}
            value={form.firstReleasedYear ?? ""}
            onChange={(event) =>
              set("firstReleasedYear", Number.parseInt(event.target.value, 10) || null)
            }
          />
          <TextField
            id="cadence"
            label="Release schedule"
            maxLength={100}
            placeholder="Weekly on Fridays"
            value={form.releaseCadence ?? ""}
            onChange={(event) => set("releaseCadence", event.target.value)}
          />
        </div>
        <div className="flex flex-col gap-2">
          <label htmlFor="cover" className="text-sm font-semibold">
            Cover
          </label>
          <div className="flex items-center gap-4">
            {cover ? (
              <img
                src={cover.url}
                width={cover.width}
                height={cover.height}
                alt="Current cover"
                className="h-36 w-24 rounded object-cover"
              />
            ) : (
              <div aria-hidden="true" className="h-36 w-24 rounded bg-raised" />
            )}
            <div className="flex flex-col gap-1">
              <input
                id="cover"
                type="file"
                accept={ACCEPTED_IMAGES}
                aria-describedby="cover-hint"
                disabled={uploading}
                onChange={(event) => void uploadCover(event.target.files?.[0])}
                className="text-sm"
              />
              <p id="cover-hint" className="text-sm text-muted">
                {uploading
                  ? "Uploading…"
                  : "PNG, JPEG or WebP, up to 20 MB. A 2:3 portrait about 1200 px wide works best."}
              </p>
            </div>
          </div>
        </div>
        <Button busy={busy || uploading} className="self-start">
          {series ? "Save series" : "Create series"}
        </Button>
      </form>
    </Section>
  );
}

function ChaptersSection({ series }: { series: AdminSeries }) {
  const router = useRouter();
  const [number, setNumber] = useState(() => nextNumber(series));
  const [title, setTitle] = useState("");
  const { busy, run, notice } = useAction();

  function add(event: FormEvent) {
    event.preventDefault();
    void run(async () => {
      const chapter = await admin.createChapter(series.id, {
        number: number.trim(),
        title: title.trim() || null,
      });
      router.push(`/admin/chapters/${chapter.id}`);
      return undefined;
    });
  }

  return (
    <Section title="Chapters">
      <form onSubmit={add} className="flex flex-wrap items-end gap-3" noValidate>
        <div className="w-28">
          <TextField
            id="new-number"
            label="Number"
            inputMode="decimal"
            maxLength={9}
            value={number}
            onChange={(event) => setNumber(event.target.value)}
          />
        </div>
        <div className="min-w-48 flex-1">
          <TextField
            id="new-title"
            label="Title (optional)"
            maxLength={300}
            value={title}
            onChange={(event) => setTitle(event.target.value)}
          />
        </div>
        <Button busy={busy}>Add chapter</Button>
      </form>
      {notice}
      {series.chapters.length === 0 ? (
        <p className="text-muted">No chapters yet.</p>
      ) : (
        <ul className="flex flex-col divide-y divide-line">
          {series.chapters.map((chapter) => (
            <li key={chapter.id} className="flex flex-wrap items-center gap-3 py-2">
              <Link
                href={`/admin/chapters/${chapter.id}`}
                className="min-w-0 flex-1 truncate font-medium hover:underline"
              >
                Chapter {chapter.number}
                {chapter.title ? `: ${chapter.title}` : ""}
              </Link>
              <span className="text-sm text-muted">
                {series.type === "MANHWA"
                  ? `${chapter.pageCount} pages`
                  : `${chapter.wordCount} words`}
              </span>
              <Badge tone={chapter.live ? "success" : "neutral"}>{releaseLabel(chapter)}</Badge>
              {chapter.publishAt ? (
                <span className="text-sm text-muted">{calendarDate(chapter.publishAt)}</span>
              ) : null}
            </li>
          ))}
        </ul>
      )}
      <p className="text-sm text-muted">{label(series.type)} · original edition. Newest first.</p>
    </Section>
  );
}

/** The next whole number after the highest chapter. */
function nextNumber(series: AdminSeries): string {
  const highest = series.chapters.reduce(
    (max, chapter) => Math.max(max, Number.parseFloat(chapter.number) || 0),
    0,
  );
  return String(Math.floor(highest) + 1);
}
