import type { Metadata } from "next";

/**
 * The public origin (SITE_URL, for example https://canreadit.example). Canonical URLs, social cards and the
 * sitemap need absolute URLs; without it they are left out rather than pointing at the wrong host (the beta's
 * quick tunnel address changes on every restart).
 */
export function siteUrl(): URL | undefined {
  const raw = process.env.SITE_URL;
  if (!raw) return undefined;
  try {
    const url = new URL(raw);
    return url.protocol === "https:" || url.protocol === "http:" ? new URL(url.origin) : undefined;
  } catch {
    return undefined;
  }
}

/** Canonical URL and og:url for a path, when the public origin is known. */
export function canonical(path: string): Pick<Metadata, "alternates" | "openGraph"> {
  return siteUrl() ? { alternates: { canonical: path }, openGraph: { url: path } } : {};
}

/** Serializes JSON-LD for an inline script; "<" is escaped so the data can't close the script tag. */
export function jsonLd(data: object): string {
  return JSON.stringify(data).replace(/</g, "\\u003c");
}
