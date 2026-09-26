import { notFound, permanentRedirect } from "next/navigation";
import createClient from "openapi-fetch";
import type { paths } from "./schema";
import type {
  ChapterContent,
  ChapterPage,
  Genre,
  HomeResponse,
  SeriesDetail,
  SeriesPage,
  SeriesStatus,
  SeriesType,
} from "./types";

/**
 * Server-side API access. Public pages never forward cookies, so every response here is the
 * same for every visitor and can be cached (CLAUDE.md section 8).
 */
const baseUrl = process.env.API_INTERNAL_URL ?? "http://localhost:8080";

function api(revalidateSeconds: number) {
  return createClient<paths>({
    baseUrl,
    fetch: (request) =>
      fetch(request, { next: { revalidate: revalidateSeconds }, redirect: "manual" }),
  });
}

export class ApiUnavailableError extends Error {}

/** `next build` runs without a backend in CI; pages render a placeholder and refresh on request. */
const isBuildPhase = () => process.env.NEXT_PHASE === "phase-production-build";

async function call<T>(
  request: () => Promise<{ data?: T; response: Response }>,
  onMoved?: (location: string) => never,
): Promise<T> {
  let result: { data?: T; response: Response };
  try {
    result = await request();
  } catch (cause) {
    throw new ApiUnavailableError("The API is unreachable", { cause });
  }
  const { data, response } = result;
  if (response.status === 301 && onMoved) {
    onMoved(response.headers.get("Location") ?? "");
  }
  if (response.status === 404 || response.status === 410) {
    notFound();
  }
  if (!response.ok || data === undefined) {
    throw new Error(`API ${response.url} answered ${response.status}`);
  }
  return data;
}

/** Old series slugs answer 301; send the browser to the same page under the new slug. */
function followSeriesMove(oldSlug: string, webPath: string) {
  return (location: string): never => {
    const newSlug = /\/series\/([a-z0-9-]+)/.exec(location)?.[1];
    if (!newSlug) {
      notFound();
    }
    permanentRedirect(webPath.replace(`/series/${oldSlug}`, `/series/${newSlug}`));
  };
}

export async function getHome(): Promise<HomeResponse | null> {
  try {
    return await call(() => api(60).GET("/api/v1/home"));
  } catch (error) {
    if (error instanceof ApiUnavailableError && isBuildPhase()) {
      return null;
    }
    throw error;
  }
}

export async function getGenres(): Promise<Genre[]> {
  return call(() => api(3600).GET("/api/v1/genres"));
}

export interface BrowseParams {
  q?: string;
  type?: SeriesType;
  status?: SeriesStatus;
  genres?: string[];
  sort?: "UPDATED" | "NEWEST" | "TITLE";
  cursor?: string;
  limit?: number;
}

export async function browseSeries(params: BrowseParams): Promise<SeriesPage> {
  return call(() => api(60).GET("/api/v1/series", { params: { query: params } }));
}

export async function getSeries(slug: string): Promise<SeriesDetail> {
  return call(
    () => api(60).GET("/api/v1/series/{slug}", { params: { path: { slug } } }),
    followSeriesMove(slug, `/series/${slug}`),
  );
}

export async function getChapters(
  slug: string,
  query: { order?: "ASC" | "DESC"; limit?: number; cursor?: string } = {},
): Promise<ChapterPage> {
  return call(
    () => api(60).GET("/api/v1/series/{slug}/chapters", { params: { path: { slug }, query } }),
    followSeriesMove(slug, `/series/${slug}`),
  );
}

export async function getChapter(slug: string, number: string): Promise<ChapterContent> {
  return call(
    () =>
      api(300).GET("/api/v1/series/{slug}/chapters/{number}", {
        params: { path: { slug, number } },
      }),
    followSeriesMove(slug, `/series/${slug}/chapter/${number}`),
  );
}
