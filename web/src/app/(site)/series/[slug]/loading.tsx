import { LoadingStatus, Skeleton } from "@/components/skeleton";

export default function Loading() {
  return (
    <main>
      <LoadingStatus what="series" />
      <div className="border-b border-line bg-raised">
        <div className="mx-auto flex max-w-6xl flex-col gap-6 px-4 py-8 sm:flex-row">
          <Skeleton
            tone="surface"
            className="aspect-[2/3] w-40 shrink-0 self-center sm:w-52 sm:self-start"
          />
          <div className="flex min-w-0 flex-1 flex-col gap-3">
            <Skeleton tone="surface" className="h-5 w-40" />
            <Skeleton tone="surface" className="h-10 w-3/4" />
            <Skeleton tone="surface" className="h-4 w-1/2" />
            <Skeleton tone="surface" className="h-5 w-32" />
            <Skeleton tone="surface" className="h-9 w-36" />
          </div>
        </div>
      </div>
      <div className="mx-auto grid max-w-6xl gap-6 px-4 py-6 lg:grid-cols-[1fr_2fr]">
        <Skeleton className="h-48 rounded-md" />
        <div className="flex flex-col gap-2">
          {Array.from({ length: 8 }, (_, i) => (
            // biome-ignore lint/suspicious/noArrayIndexKey: static placeholders that never reorder
            <Skeleton key={i} className="h-12 w-full" />
          ))}
        </div>
      </div>
    </main>
  );
}
