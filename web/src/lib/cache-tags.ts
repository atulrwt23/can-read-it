/**
 * Tags on cached API responses. The API asks the web app to revalidate them when content changes (for example when
 * a scheduled chapter goes live), so cached pages refresh within seconds instead of waiting out their timer.
 */
export const cacheTags = {
  home: "home",
  genres: "genres",
  seriesLists: "series-lists",
  series: (slug: string) => `series:${slug}`,
} as const;

const TAG = /^[a-z0-9]+(?:[:-][a-z0-9]+)*$/;
const MAX_TAGS = 20;

/** The tags from a revalidation request, or null when the body isn't a short list of well-formed tags. */
export function parseRevalidateTags(body: unknown): string[] | null {
  if (typeof body !== "object" || body === null || !("tags" in body)) return null;
  const { tags } = body as { tags: unknown };
  if (!Array.isArray(tags) || tags.length === 0 || tags.length > MAX_TAGS) return null;
  if (!tags.every((tag) => typeof tag === "string" && tag.length <= 200 && TAG.test(tag)))
    return null;
  return [...new Set(tags as string[])];
}
