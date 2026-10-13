import type { components } from "@/lib/api/schema";
import { api } from "@/lib/auth/client";

type Schemas = components["schemas"];
export type AdminSeriesList = Schemas["AdminSeriesList"];
export type AdminSeriesItem = Schemas["AdminSeriesItem"];
export type AdminSeries = Schemas["AdminSeries"];
export type AdminChapter = Schemas["AdminChapter"];
export type AdminChapterItem = Schemas["AdminChapterItem"];
export type AdminPage = Schemas["AdminPage"];
export type SeriesForm = Schemas["SeriesForm"];
export type ChapterForm = Schemas["ChapterForm"];
export type UploadedImage = Schemas["UploadedImage"];
export type ImageKind = "covers" | "pages";

const base = "/api/v1/admin";

/** Staff catalog editing (`/api/v1/admin`). Every call needs an account with the matching permission. */
export const admin = {
  listSeries: (q: string, offset = 0, limit = 50) => {
    const params = new URLSearchParams({ offset: String(offset), limit: String(limit) });
    if (q.trim()) params.set("q", q.trim());
    return api<AdminSeriesList>("GET", `${base}/series?${params}`);
  },
  series: (id: string) => api<AdminSeries>("GET", `${base}/series/${id}`),
  createSeries: (form: SeriesForm) => api<AdminSeries>("POST", `${base}/series`, form),
  updateSeries: (id: string, form: SeriesForm) =>
    api<AdminSeries>("PUT", `${base}/series/${id}`, form),
  createChapter: (seriesId: string, form: ChapterForm) =>
    api<AdminChapter>("POST", `${base}/series/${seriesId}/chapters`, form),
  chapter: (id: string) => api<AdminChapter>("GET", `${base}/chapters/${id}`),
  updateChapter: (id: string, form: ChapterForm) =>
    api<AdminChapter>("PUT", `${base}/chapters/${id}`, form),
  setPages: (id: string, assetIds: string[]) =>
    api<AdminChapter>("PUT", `${base}/chapters/${id}/pages`, { assetIds }),
  setBody: (id: string, markdown: string) =>
    api<AdminChapter>("PUT", `${base}/chapters/${id}/body`, { markdown }),
  /** Without a time, the chapter is released now. */
  schedule: (id: string, publishAt?: string) =>
    api<AdminChapter>("POST", `${base}/chapters/${id}/schedule`, { publishAt }),
  unschedule: (id: string) => api<AdminChapter>("POST", `${base}/chapters/${id}/unschedule`),
  deleteChapter: (id: string) => api("DELETE", `${base}/chapters/${id}`),
  /** Uploads one image; the API converts it to WebP before answering. */
  upload: (kind: ImageKind, file: File) => {
    const body = new FormData();
    body.append("file", file);
    return api<UploadedImage>("POST", `${base}/media/${kind}`, body);
  },
};

/** Page uploads are capped by the API (20 MB) and by the edge's request limit. */
export const MAX_UPLOAD_BYTES = 20 * 1024 * 1024;
export const ACCEPTED_IMAGES = "image/png,image/jpeg,image/webp";

/** Staff screens show only to accounts with an admin role; the API checks permissions on every call. */
export function isStaff(roles: readonly string[]): boolean {
  return roles.includes("ADMIN");
}

/** `datetime-local` input value (local time, minutes) for an ISO instant, and back. */
export function toLocalInput(iso: string): string {
  const date = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

export function fromLocalInput(value: string): string | null {
  if (!value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

/** Moves the item at `from` to `to` (both in range), returning a new array. */
export function move<T>(items: readonly T[], from: number, to: number): T[] {
  const next = [...items];
  const [item] = next.splice(from, 1);
  if (item !== undefined) next.splice(to, 0, item);
  return next;
}
