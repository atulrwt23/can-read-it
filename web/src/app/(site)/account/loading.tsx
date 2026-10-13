import { LoadingStatus, Skeleton } from "@/components/skeleton";

/** Shaped like the centred auth and account cards. */
export default function Loading() {
  return (
    <main className="mx-auto flex max-w-md flex-col gap-4 px-4 py-12">
      <LoadingStatus what="page" />
      <Skeleton className="h-96 w-full rounded-md" />
    </main>
  );
}
