"use client";

import { useEffect } from "react";
import { type AnalyticsEvent, track } from "@/lib/analytics";

/** Sends one event when a server-rendered page appears in the browser (pages are cached, so the server can't). */
export function TrackEvent({ event }: { event: AnalyticsEvent }) {
  // A key of the event's values, so the effect runs once per distinct event rather than on every render.
  const key = JSON.stringify(event);
  useEffect(() => {
    track(JSON.parse(key) as AnalyticsEvent);
  }, [key]);
  return null;
}
