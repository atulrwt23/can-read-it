import type { ImageRef } from "@/lib/api/types";

interface CoverProps {
  image: ImageRef | null | undefined;
  title: string;
  className?: string;
  /** Above-the-fold covers load immediately; the rest lazily. */
  eager?: boolean;
}

/** A 2:3 series cover with intrinsic size, or a flat token-coloured placeholder. */
export function Cover({ image, title, className = "", eager = false }: CoverProps) {
  if (!image) {
    return (
      <div
        role="img"
        aria-label={title}
        className={`flex aspect-[2/3] items-center justify-center rounded bg-raised text-2xl font-bold text-muted ${className}`}
      >
        {title.charAt(0)}
      </div>
    );
  }
  return (
    <img
      src={image.url}
      width={image.width}
      height={image.height}
      alt={title}
      loading={eager ? "eager" : "lazy"}
      decoding="async"
      className={`aspect-[2/3] w-full rounded bg-raised object-cover ${className}`}
    />
  );
}
