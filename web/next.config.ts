import type { NextConfig } from "next";

// Server-side base URL of the API. The browser never uses it: it calls same-origin /api/*.
const apiInternalUrl = process.env.API_INTERNAL_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  output: "standalone",
  poweredByHeader: false,
  // Images come from our CDN with known sizes; there is nothing for Next to optimize.
  images: { unoptimized: true },
  async rewrites() {
    // Local development only: in production the edge routes /api/* to the backend first.
    return [{ source: "/api/:path*", destination: `${apiInternalUrl}/api/:path*` }];
  },
};

export default nextConfig;
