import type { Metadata } from "next";

export const metadata: Metadata = { title: "Library" };

export default function Page() {
  return (
    <main className="mx-auto max-w-md px-4 py-16">
      <div className="rounded-md bg-surface p-6 text-center shadow-card">
        <h1 className="mb-2 text-2xl">Library</h1>
        <p className="text-muted">
          Your library, follows and reading history arrive in roadmap step 4.
        </p>
      </div>
    </main>
  );
}
