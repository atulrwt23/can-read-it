import type { Metadata } from "next";

export const metadata: Metadata = { title: "Sign in" };

export default function Page() {
  return (
    <main className="mx-auto max-w-md px-4 py-16">
      <div className="rounded-md bg-surface p-6 text-center shadow-card">
        <h1 className="mb-2 text-2xl">Sign in</h1>
        <p className="text-muted">
          Sign-in arrives in roadmap step 2. Reading never needs an account.
        </p>
      </div>
    </main>
  );
}
