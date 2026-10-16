import { LoadingStatus, Skeleton } from "@/components/skeleton";

/** Shaped like the unsubscribe card. */
export default function Loading() {
  return (
    <main className="mx-auto flex max-w-md flex-col gap-4 px-4 py-12">
      <LoadingStatus what="page" />
      <Skeleton className="h-48 w-full rounded-md" />
    </main>
  );
}
