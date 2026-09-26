import type { Metadata, Viewport } from "next";
import type { ReactNode } from "react";
import { SiteHeader } from "@/components/site-header";
import { THEME_SCRIPT } from "@/components/theme-toggle";
import { BRAND_NAME } from "@/lib/brand";
import "./globals.css";

export const metadata: Metadata = {
  title: { default: `${BRAND_NAME}: manhwa and web novels`, template: `%s · ${BRAND_NAME}` },
  description: "Read manhwa and web novels, follow series and catch every new chapter.",
};

export const viewport: Viewport = {
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#ffffff" },
    { media: "(prefers-color-scheme: dark)", color: "#1c1f24" },
  ],
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en" suppressHydrationWarning>
      <head>
        {/* biome-ignore lint/security/noDangerouslySetInnerHtml: static theme bootstrap, no user input */}
        <script dangerouslySetInnerHTML={{ __html: THEME_SCRIPT }} />
      </head>
      <body className="min-h-dvh bg-bg text-text antialiased">
        <a
          href="#main"
          className="sr-only focus:not-sr-only focus:absolute focus:z-50 focus:m-2 focus:rounded focus:bg-surface focus:px-3 focus:py-2"
        >
          Skip to content
        </a>
        <SiteHeader />
        <div id="main">{children}</div>
        <footer className="mt-16 border-t border-line py-8 text-center text-sm text-muted">
          {BRAND_NAME} · placeholder content for development
        </footer>
      </body>
    </html>
  );
}
