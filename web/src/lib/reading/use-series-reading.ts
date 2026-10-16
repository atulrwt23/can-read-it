"use client";

import { useEffect, useState } from "react";
import { type SeriesReading, seriesReading } from "./store";

/**
 * The reader's state for a series, loaded after hydration (series pages are the same for every visitor).
 * `undefined` until known; a failed request leaves it unknown rather than empty.
 */
export function useSeriesReading(
  slug: string,
): [SeriesReading | undefined, (next: SeriesReading) => void] {
  const [state, setState] = useState<SeriesReading | undefined>();
  useEffect(() => {
    let current = true;
    seriesReading(slug)
      .then((next) => current && setState(next))
      .catch(() => undefined);
    return () => {
      current = false;
    };
  }, [slug]);
  return [state, setState];
}
