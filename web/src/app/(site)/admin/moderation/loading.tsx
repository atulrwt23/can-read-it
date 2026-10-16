import { LoadingStatus, Skeleton } from "@/components/skeleton";

/** Shaped like the staff pages: a heading over a card. */
export default function Loading() {
  return (
    <main className="mx-auto flex max-w-4xl flex-col gap-6 px-4 py-8">
      <LoadingStatus what="page" />
      <Skeleton className="h-9 w-48" />
      <Skeleton className="h-96 w-full rounded-md" />
    </main>
  );
}
