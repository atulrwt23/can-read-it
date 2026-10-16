import { CardGridSkeleton, LoadingStatus, Skeleton } from "@/components/skeleton";

/** Shaped like the library: a heading over a grid of covers. */
export default function Loading() {
  return (
    <main className="mx-auto flex max-w-6xl flex-col gap-8 px-4 py-8">
      <LoadingStatus what="library" />
      <Skeleton className="h-9 w-40" />
      <CardGridSkeleton
        count={6}
        className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6"
      />
    </main>
  );
}
