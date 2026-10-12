import { createSettingsStore } from "./local-settings";

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

export const useNovelSettings = createSettingsStore(
  "canreadit:novel-settings:v1",
  DEFAULT_SETTINGS,
  sanitize,
);
