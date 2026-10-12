import { LoadingStatus, Skeleton } from "@/components/skeleton";

/** The reader's shape: a bar on top and a column of page-sized blocks. */
export default function Loading() {
  return (
    <>
      <LoadingStatus what="chapter" />
      <div className="fixed inset-x-0 top-0 z-20 h-14 border-b border-line bg-surface" />
      <main className="mx-auto flex max-w-[800px] flex-col gap-1 pt-14">
        <Skeleton className="h-[80vh] w-full" />
        <Skeleton className="h-[60vh] w-full" />
      </main>
    </>
  );
}
