import { createSettingsStore } from "./local-settings";

/** Manhwa reader preferences, remembered per browser. */
export interface MangaSettings {
  /** Maximum width of the page strip on wide screens; "full" fills the window. */
  width: (typeof WIDTHS)[number];
}

export const WIDTHS = ["narrow", "standard", "wide", "full"] as const;

/** CSS max-width per setting. Pages are stored about 1080px wide, so "full" may upscale on large screens. */
export const WIDTH_CSS: Record<MangaSettings["width"], string> = {
  narrow: "600px",
  standard: "800px",
  wide: "1080px",
  full: "100%",
};

export const DEFAULT_MANGA_SETTINGS: MangaSettings = { width: "standard" };

export function sanitizeMangaSettings(value: unknown): MangaSettings {
  const width = (value as Partial<MangaSettings> | null)?.width;
  return { width: WIDTHS.find((w) => w === width) ?? DEFAULT_MANGA_SETTINGS.width };
}

export const useMangaSettings = createSettingsStore(
  "canreadit:manga-settings:v1",
  DEFAULT_MANGA_SETTINGS,
  sanitizeMangaSettings,
);
