const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

/** Compact relative time: "just now", "5m ago", "3h ago", "2d ago", "3w ago", then a date. */
export function relativeTime(iso: string, now: Date = new Date()): string {
  const elapsed = now.getTime() - new Date(iso).getTime();
  if (elapsed < MINUTE) return "just now";
  if (elapsed < HOUR) return `${Math.floor(elapsed / MINUTE)}m ago`;
  if (elapsed < DAY) return `${Math.floor(elapsed / HOUR)}h ago`;
  if (elapsed < 7 * DAY) return `${Math.floor(elapsed / DAY)}d ago`;
  if (elapsed < 30 * DAY) return `${Math.floor(elapsed / (7 * DAY))}w ago`;
  return new Date(iso).toLocaleDateString("en", {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
}

/** Chapters released within the last day get a "new" badge. */
export function isNew(iso: string, now: Date = new Date()): boolean {
  return now.getTime() - new Date(iso).getTime() < DAY;
}

export function compactNumber(value: number): string {
  return new Intl.NumberFormat("en", { notation: "compact", maximumFractionDigits: 1 }).format(
    value,
  );
}

const LABELS: Record<string, string> = {
  MANHWA: "Manhwa",
  NOVEL: "Novel",
  ONGOING: "Ongoing",
  COMPLETED: "Completed",
  HIATUS: "Hiatus",
  ALL: "All ages",
  TEEN: "Teen",
  MATURE: "Mature",
};

export function label(value: string): string {
  return LABELS[value] ?? value;
}
