"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { type ReactNode, useCallback, useState } from "react";
import { Notice } from "@/components/auth/form";
import { useSession } from "@/components/auth/session";
import { Skeleton } from "@/components/skeleton";
import { isStaff } from "@/lib/admin/client";
import { errorMessage } from "@/lib/auth/client";

/** The staff page frame: a heading, breadcrumbs back to the list, and the content once access is confirmed. */
export function AdminShell({
  title,
  back,
  actions,
  children,
}: {
  title: string;
  back?: { href: string; label: string };
  actions?: ReactNode;
  children: ReactNode;
}) {
  return (
    <main className="mx-auto flex max-w-4xl flex-col gap-6 px-4 py-8">
      <div className="flex flex-col gap-1">
        {back ? (
          <Link href={back.href} className="self-start text-sm text-link underline">
            ← {back.label}
          </Link>
        ) : null}
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h1 className="text-3xl">{title}</h1>
          {actions}
        </div>
      </div>
      <StaffOnly>{children}</StaffOnly>
    </main>
  );
}

/** Shows its children to signed-in staff only. The API enforces this too; here it just explains. */
function StaffOnly({ children }: { children: ReactNode }) {
  const session = useSession();
  const pathname = usePathname();
  if (session.status === "loading") {
    return (
      <>
        <p role="status" className="sr-only">
          Checking your access…
        </p>
        <Skeleton className="h-64 w-full rounded-md" />
      </>
    );
  }
  if (session.status === "signed-out") {
    return (
      <Section title="Sign in to continue">
        <p>The catalog tools are for staff. Sign in with a staff account.</p>
        <Link
          href={`/login?next=${encodeURIComponent(pathname)}`}
          className="self-start rounded bg-primary px-4 py-2 text-sm font-semibold text-on-primary"
        >
          Sign in
        </Link>
      </Section>
    );
  }
  if (!isStaff(session.me.roles)) {
    return (
      <Section title="No access">
        <p>Your account can't edit the catalog.</p>
        <Link href="/" className="self-start text-link underline">
          Back to reading
        </Link>
      </Section>
    );
  }
  return children;
}

export function Section({
  title,
  actions,
  children,
}: {
  title: string;
  actions?: ReactNode;
  children: ReactNode;
}) {
  const id = `section-${title.toLowerCase().replace(/[^a-z0-9]+/g, "-")}`;
  return (
    <section
      aria-labelledby={id}
      className="flex flex-col gap-4 rounded-md bg-surface p-6 shadow-card"
    >
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 id={id} className="text-xl">
          {title}
        </h2>
        {actions}
      </div>
      {children}
    </section>
  );
}

/** Runs an action, tracking busy state and a result message. */
export function useAction() {
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ kind: "error" | "info"; text: ReactNode } | null>(null);
  const run = useCallback(async (action: () => Promise<ReactNode | undefined>) => {
    setBusy(true);
    setMessage(null);
    try {
      const done = await action();
      if (done) setMessage({ kind: "info", text: done });
    } catch (caught) {
      setMessage({ kind: "error", text: errorMessage(caught) });
    } finally {
      setBusy(false);
    }
  }, []);
  const notice = message ? <Notice kind={message.kind}>{message.text}</Notice> : null;
  return { busy, run, notice, setMessage };
}

/** A chapter's release state in words, for lists and the chapter page. */
export function releaseLabel(chapter: {
  status: string;
  live: boolean;
  publishAt?: string | null;
}): string {
  if (chapter.live) return "Live";
  if (chapter.status === "SCHEDULED") return "Scheduled";
  return "Draft";
}

export const linkButton =
  "inline-flex h-10 items-center justify-center rounded bg-primary px-4 text-sm font-semibold text-on-primary hover:brightness-95";
