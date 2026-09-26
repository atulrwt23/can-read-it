import type { ReactNode } from "react";
import { SiteHeader } from "@/components/site-header";
import { BRAND_NAME } from "@/lib/brand";

/** Chrome for everything except the reader, which has its own auto-hiding bar. */
export default function SiteLayout({ children }: { children: ReactNode }) {
  return (
    <>
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
    </>
  );
}
