import type { MetadataRoute } from "next";
import { siteUrl } from "@/lib/site";

/**
 * Search engines stay out until launch: the beta serves invented placeholder content. Set
 * ALLOW_INDEXING=true at build time to open up (next.config.ts adds X-Robots-Tag to match).
 * With SITE_URL set, the rules also point crawlers at the sitemap.
 */
export default function robots(): MetadataRoute.Robots {
  if (process.env.ALLOW_INDEXING !== "true") {
    return { rules: { userAgent: "*", disallow: "/" } };
  }
  const origin = siteUrl();
  return {
    rules: { userAgent: "*", allow: "/", disallow: ["/api/", "/internal/"] },
    sitemap: origin ? new URL("/sitemap.xml", origin).href : undefined,
  };
}
