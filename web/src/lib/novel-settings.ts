import { useCallback, useSyncExternalStore } from "react";

/** Novel reader preferences, remembered per browser. */
export interface NovelSettings {
  size: number;
  spacing: "normal" | "relaxed" | "loose";
  font: "serif" | "sans";
  page: "theme" | "light" | "sepia" | "dark";
}

export const SIZES = [16, 18, 20, 22, 24] as const;
export const DEFAULT_SETTINGS: NovelSettings = {
  size: 18,
  spacing: "relaxed",
  font: "serif",
  page: "theme",
};

const KEY = "canreadit:novel-settings:v1";
const listeners = new Set<() => void>();
let cached: NovelSettings | undefined;

function sanitize(value: unknown): NovelSettings {
  const v = (value && typeof value === "object" ? value : {}) as Partial<NovelSettings>;
  return {
    size: SIZES.includes(v.size as (typeof SIZES)[number])
      ? (v.size as number)
      : DEFAULT_SETTINGS.size,
    spacing: v.spacing === "normal" || v.spacing === "loose" ? v.spacing : "relaxed",
    font: v.font === "sans" ? "sans" : "serif",
    page: v.page === "light" || v.page === "sepia" || v.page === "dark" ? v.page : "theme",
  };
}

function snapshot(): NovelSettings {
  if (!cached) {
    try {
      cached = sanitize(JSON.parse(localStorage.getItem(KEY) ?? "{}"));
    } catch {
      cached = DEFAULT_SETTINGS;
    }
  }
  return cached;
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function useNovelSettings(): [NovelSettings, (change: Partial<NovelSettings>) => void] {
  const settings = useSyncExternalStore(subscribe, snapshot, () => DEFAULT_SETTINGS);
  const update = useCallback((change: Partial<NovelSettings>) => {
    cached = sanitize({ ...snapshot(), ...change });
    try {
      localStorage.setItem(KEY, JSON.stringify(cached));
    } catch {
      // Not persisted, but still applied for this visit.
    }
    for (const listener of listeners) listener();
  }, []);
  return [settings, update];
}
