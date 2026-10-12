import type { MetadataRoute } from "next";
import { headers } from "next/headers";
import { allPublishedSeries } from "@/lib/api/server";
import { seriesPath } from "@/lib/routes";
import { siteUrl } from "@/lib/site";

/**
 * /sitemap.xml: the home and browse pages and every published series. Chapters are reached from their series page.
 * URLs use SITE_URL, or the host the request came in on. Built on request, with the series list cached for an hour.
 */
export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const origin = siteUrl() ?? (await requestOrigin());
  const url = (path: string) => new URL(path, origin).href;
  const series = await allPublishedSeries();
  return [
    { url: url("/"), changeFrequency: "hourly", priority: 1 },
    { url: url("/browse"), changeFrequency: "daily", priority: 0.5 },
    ...series.map((entry) => ({
      url: url(seriesPath(entry.slug)),
      lastModified: entry.lastPublishedAt ?? undefined,
      changeFrequency: "daily" as const,
      priority: 0.8,
    })),
  ];
}

async function requestOrigin(): Promise<URL> {
  const list = await headers();
  const host = list.get("x-forwarded-host") ?? list.get("host") ?? "localhost:3000";
  const proto = list.get("x-forwarded-proto") ?? (host.startsWith("localhost") ? "http" : "https");
  return new URL(`${proto}://${host}`);
}
