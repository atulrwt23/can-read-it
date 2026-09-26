import type { NextConfig } from "next";

// Server-side base URL of the API. The browser never uses it: it calls same-origin /api/*.
const apiInternalUrl = process.env.API_INTERNAL_URL ?? "http://localhost:8080";

// Build-time switch: until launch the site serves placeholder content and must not be indexed.
const allowIndexing = process.env.ALLOW_INDEXING === "true";

const nextConfig: NextConfig = {
  output: "standalone",
  poweredByHeader: false,
  // Images come from our CDN with known sizes; there is nothing for Next to optimize.
  images: { unoptimized: true },
  async headers() {
    return allowIndexing
      ? []
      : [{ source: "/:path*", headers: [{ key: "X-Robots-Tag", value: "noindex, nofollow" }] }];
  },
  async rewrites() {
    // Proxies /api/* to the backend. With a named tunnel the edge routes /api/* to the backend
    // before it reaches Next; with a quick tunnel (single origin) this rewrite does it.
    // Rewrites are fixed at build time, so images are built with API_INTERNAL_URL=http://api:8080.
    return [{ source: "/api/:path*", destination: `${apiInternalUrl}/api/:path*` }];
  },
};

export default nextConfig;
