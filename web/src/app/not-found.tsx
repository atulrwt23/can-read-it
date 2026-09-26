import Link from "next/link";

export default function NotFound() {
  return (
    <main className="mx-auto max-w-md px-4 py-16 text-center">
      <h1 className="mb-2 text-3xl">Not found</h1>
      <p className="mb-6 text-muted">
        This page does not exist, or the series is no longer available.
      </p>
      <Link
        href="/browse"
        className="rounded bg-primary px-4 py-2 text-sm font-semibold text-on-primary"
      >
        Browse series
      </Link>
    </main>
  );
}
