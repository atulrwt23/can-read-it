import type { Metadata } from "next";

export const metadata: Metadata = { title: "Create an account" };

export default function Page() {
  return (
    <main className="mx-auto max-w-md px-4 py-16">
      <div className="rounded-md bg-surface p-6 text-center shadow-card">
        <h1 className="mb-2 text-2xl">Create an account</h1>
        <p className="text-muted">
          Accounts arrive in roadmap step 2. Reading never needs an account.
        </p>
      </div>
    </main>
  );
}
