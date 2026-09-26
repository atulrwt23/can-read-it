"use client";

import { Markdown } from "@/lib/markdown";
import { SIZES, useNovelSettings } from "@/lib/novel-settings";

const PAGES = {
  theme: { background: "var(--surface)", color: "var(--text)" },
  light: { background: "#ffffff", color: "#363636" },
  sepia: { background: "#f4ecd8", color: "#5b4636" },
  dark: { background: "#14161a", color: "#e6e8eb" },
} as const;

const LINE_HEIGHTS = { normal: 1.5, relaxed: 1.75, loose: 2 } as const;

export function NovelBody({ markdown }: { markdown: string }) {
  const [settings] = useNovelSettings();
  const page = PAGES[settings.page];
  return (
    <article
      style={{
        background: page.background,
        color: page.color,
        fontSize: `${settings.size}px`,
        lineHeight: LINE_HEIGHTS[settings.spacing],
        fontFamily: settings.font === "serif" ? "var(--font-serif)" : "var(--font-sans)",
      }}
      className="mx-auto max-w-[70ch] rounded-md px-5 py-8 sm:px-10 [&_h2]:mt-8 [&_h2]:mb-4 [&_h2]:text-2xl [&_h3]:mt-6 [&_h3]:mb-3 [&_h3]:text-xl [&_hr]:my-8 [&_hr]:border-current [&_hr]:opacity-30 [&_p]:mb-[1em]"
    >
      <Markdown source={markdown} />
    </article>
  );
}

const option =
  "rounded border border-line px-3 py-1.5 text-sm font-medium aria-pressed:border-primary aria-pressed:bg-primary aria-pressed:text-on-primary";

/** "Aa" button with a popover of reading settings. */
export function NovelSettingsButton() {
  const [settings, update] = useNovelSettings();
  const sizeIndex = SIZES.indexOf(settings.size as (typeof SIZES)[number]);
  return (
    <>
      <button
        type="button"
        popoverTarget="novel-settings"
        aria-label="Reading settings"
        className="inline-flex size-9 items-center justify-center rounded font-serif text-lg font-bold hover:bg-raised"
      >
        Aa
      </button>
      <div
        id="novel-settings"
        popover="auto"
        role="dialog"
        aria-label="Reading settings"
        className="m-0 mt-14 ml-auto mr-2 w-72 rounded-md border border-line bg-surface p-4 text-text shadow-card"
      >
        <fieldset className="mb-4">
          <legend className="mb-2 text-sm text-muted">Text size</legend>
          <div className="flex items-center gap-2">
            <button
              type="button"
              className={option}
              aria-label="Smaller text"
              disabled={sizeIndex <= 0}
              onClick={() => update({ size: SIZES[sizeIndex - 1] })}
            >
              A−
            </button>
            <span className="flex-1 text-center text-sm">{settings.size}px</span>
            <button
              type="button"
              className={option}
              aria-label="Larger text"
              disabled={sizeIndex >= SIZES.length - 1}
              onClick={() => update({ size: SIZES[sizeIndex + 1] })}
            >
              A+
            </button>
          </div>
        </fieldset>
        <fieldset className="mb-4">
          <legend className="mb-2 text-sm text-muted">Line spacing</legend>
          <div className="flex gap-2">
            {(["normal", "relaxed", "loose"] as const).map((spacing) => (
              <button
                key={spacing}
                type="button"
                aria-pressed={settings.spacing === spacing}
                className={option}
                onClick={() => update({ spacing })}
              >
                {spacing[0]?.toUpperCase() + spacing.slice(1)}
              </button>
            ))}
          </div>
        </fieldset>
        <fieldset className="mb-4">
          <legend className="mb-2 text-sm text-muted">Font</legend>
          <div className="flex gap-2">
            <button
              type="button"
              aria-pressed={settings.font === "serif"}
              className={`${option} font-serif`}
              onClick={() => update({ font: "serif" })}
            >
              Serif
            </button>
            <button
              type="button"
              aria-pressed={settings.font === "sans"}
              className={`${option} font-sans`}
              onClick={() => update({ font: "sans" })}
            >
              Sans
            </button>
          </div>
        </fieldset>
        <fieldset>
          <legend className="mb-2 text-sm text-muted">Page colour</legend>
          <div className="flex flex-wrap gap-2">
            {(["theme", "light", "sepia", "dark"] as const).map((page) => (
              <button
                key={page}
                type="button"
                aria-pressed={settings.page === page}
                className={option}
                onClick={() => update({ page })}
              >
                <span
                  aria-hidden="true"
                  className="mr-1.5 inline-block size-3 rounded-full border border-line align-middle"
                  style={{ background: PAGES[page].background }}
                />
                {page === "theme" ? "Auto" : page[0]?.toUpperCase() + page.slice(1)}
              </button>
            ))}
          </div>
        </fieldset>
      </div>
    </>
  );
}
