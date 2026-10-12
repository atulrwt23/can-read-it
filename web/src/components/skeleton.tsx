/**
 * A flat placeholder block shown while a page loads. It pulses unless the reader prefers reduced motion. Use
 * `tone="surface"` on raised backgrounds, where the default would not show.
 */
export function Skeleton({
  className = "",
  tone = "raised",
}: {
  className?: string;
  tone?: "raised" | "surface";
}) {
  return (
    <div
      aria-hidden="true"
      className={`animate-pulse rounded motion-reduce:animate-none ${tone === "surface" ? "bg-surface" : "bg-raised"} ${className}`}
    />
  );
}

/** A grid of series-card placeholders, shaped like SeriesGridCard. */
export function CardGridSkeleton({ count, className }: { count: number; className: string }) {
  return (
    <div className={className}>
      {Array.from({ length: count }, (_, i) => (
        // biome-ignore lint/suspicious/noArrayIndexKey: static placeholders that never reorder
        <div key={i} className="flex flex-col gap-2 rounded-md bg-surface p-2 shadow-card">
          <Skeleton className="aspect-[2/3] w-full" />
          <Skeleton className="h-4 w-3/4" />
          <Skeleton className="h-3 w-1/2" />
        </div>
      ))}
    </div>
  );
}

/** Announces the wait to screen readers; the skeleton itself is hidden from them. */
export function LoadingStatus({ what }: { what: string }) {
  return (
    <p role="status" className="sr-only">
      Loading {what}…
    </p>
  );
}
