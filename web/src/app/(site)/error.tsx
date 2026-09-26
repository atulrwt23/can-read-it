"use client";

export default function ErrorPage({ reset }: { error: Error; reset: () => void }) {
  return (
    <main className="mx-auto max-w-md px-4 py-16 text-center">
      <h1 className="mb-2 text-3xl">Something went wrong</h1>
      <p className="mb-6 text-muted">We could not load this page. Please try again.</p>
      <button
        type="button"
        onClick={reset}
        className="rounded bg-primary px-4 py-2 text-sm font-semibold text-on-primary"
      >
        Try again
      </button>
    </main>
  );
}
