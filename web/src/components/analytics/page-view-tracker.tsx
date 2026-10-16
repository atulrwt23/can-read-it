"use client";

import { usePathname } from "next/navigation";
import { useEffect } from "react";
import { routeTemplate, track } from "@/lib/analytics";

/** Counts each page shown, by route template (never the full URL). */
export function PageViewTracker() {
  const pathname = usePathname();
  useEffect(() => {
    const path = routeTemplate(pathname);
    if (path) track({ name: "PAGE_VIEW", path });
  }, [pathname]);
  return null;
}
