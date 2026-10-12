import { CardGridSkeleton, LoadingStatus, Skeleton } from "@/components/skeleton";

/** Shown while a page in the site loads; shaped like the home page. */
export default function Loading() {
  return (
    <main className="mx-auto flex max-w-6xl flex-col gap-8 px-4 py-6">
      <LoadingStatus what="page" />
      <Skeleton className="h-56 w-full rounded-md md:h-64" />
      <div className="grid gap-6 lg:grid-cols-[1fr_320px]">
        <div>
          <Skeleton className="mb-3 h-6 w-40" />
          <CardGridSkeleton
            count={8}
            className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4"
          />
        </div>
        <Skeleton className="h-96 rounded-md" />
      </div>
    </main>
  );
}
