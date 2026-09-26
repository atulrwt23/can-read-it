import type { MetadataRoute } from "next";

/**
 * Search engines stay out until launch: the beta serves invented placeholder content. Set
 * ALLOW_INDEXING=true at build time to open up (next.config.ts adds X-Robots-Tag to match).
 */
export default function robots(): MetadataRoute.Robots {
  return process.env.ALLOW_INDEXING === "true"
    ? { rules: { userAgent: "*", allow: "/" } }
    : { rules: { userAgent: "*", disallow: "/" } };
}
