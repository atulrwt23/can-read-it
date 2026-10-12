import { CardGridSkeleton, LoadingStatus, Skeleton } from "@/components/skeleton";

export default function Loading() {
  return (
    <main className="mx-auto flex max-w-6xl flex-col gap-6 px-4 py-6">
      <LoadingStatus what="series" />
      <Skeleton className="h-9 w-48" />
      <Skeleton className="h-40 w-full rounded-md" />
      <Skeleton className="h-4 w-24" />
      <CardGridSkeleton
        count={12}
        className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6"
      />
    </main>
  );
}
