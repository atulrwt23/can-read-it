import type { Metadata, Viewport } from "next";
import type { ReactNode } from "react";
import { PageViewTracker } from "@/components/analytics/page-view-tracker";
import { THEME_SCRIPT } from "@/components/theme-toggle";
import { BRAND_NAME } from "@/lib/brand";
import { siteUrl } from "@/lib/site";
import "./globals.css";

const DESCRIPTION = "Read manhwa and web novels, follow series and catch every new chapter.";

export function generateMetadata(): Metadata {
  return {
    metadataBase: siteUrl(),
    title: { default: `${BRAND_NAME}: manhwa and web novels`, template: `%s · ${BRAND_NAME}` },
    description: DESCRIPTION,
    applicationName: BRAND_NAME,
    openGraph: { siteName: BRAND_NAME, type: "website", locale: "en" },
    twitter: { card: "summary_large_image" },
  };
}

export const viewport: Viewport = {
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#ffffff" },
    { media: "(prefers-color-scheme: dark)", color: "#1c1f24" },
  ],
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body className="min-h-dvh bg-bg text-text antialiased">
        {/*
          Sets data-theme before anything visible is parsed, so there is no theme flash. It sits
          in <body>, not <head>: React hydrates <head> as soon as cached app chunks run, which can
          be before the parser reaches an inline script there (hydration error #418).
        */}
        {/* biome-ignore lint/security/noDangerouslySetInnerHtml: static theme bootstrap, no user input */}
        <script dangerouslySetInnerHTML={{ __html: THEME_SCRIPT }} />
        {children}
        <PageViewTracker />
      </body>
    </html>
  );
}
