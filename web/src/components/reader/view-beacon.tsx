"use client";

import { useEffect } from "react";

/**
 * Tells the API a chapter of this series was opened, for the rankings. The API counts it once per visitor, series
 * and day, so re-renders and reloads are harmless. Fire-and-forget: failures are ignored.
 */
export function ViewBeacon({ slug }: { slug: string }) {
  useEffect(() => {
    const url = `/api/v1/series/${encodeURIComponent(slug)}/views`;
    if (!navigator.sendBeacon?.(url)) {
      fetch(url, { method: "POST", keepalive: true }).catch(() => {});
    }
  }, [slug]);
  return null;
}
