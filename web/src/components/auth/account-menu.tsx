"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useSession } from "./session";

function signInHref(pathname: string): string {
  return pathname === "/" || pathname.startsWith("/login") || pathname.startsWith("/signup")
    ? "/login"
    : `/login?next=${encodeURIComponent(pathname)}`;
}

/**
 * The header's sign-in link, or the signed-in reader's menu. The server render (and the first client render)
 * always shows "Sign in", so public pages stay identical for everyone; the menu appears after hydration.
 */
export function AccountMenu() {
  const session = useSession();
  const pathname = usePathname();

  if (session.status !== "signed-in") {
    return (
      <Link
        href={signInHref(pathname)}
        className="hidden rounded bg-primary px-3 py-1.5 text-sm font-semibold text-on-primary hover:brightness-95 md:inline-block"
      >
        Sign in
      </Link>
    );
  }
  const name = session.me.displayName;
  return (
    <>
      <button
        type="button"
        popoverTarget="account-menu"
        aria-label={`Account menu for ${name}`}
        className="hidden size-9 items-center justify-center rounded-full bg-raised text-sm font-bold uppercase hover:brightness-95 md:inline-flex"
      >
        <span aria-hidden="true">{name.slice(0, 1)}</span>
      </button>
      <div
        id="account-menu"
        popover="auto"
        className="m-0 mt-14 mr-4 ml-auto w-56 rounded-md border border-line bg-surface p-2 text-text shadow-card"
      >
        <p className="truncate px-3 py-2 text-sm text-muted">Signed in as {name}</p>
        <AccountMenuItems />
      </div>
    </>
  );
}

/** "Account" and "Sign out", or "Sign in": shared by the account menu and the mobile menu. */
export function AccountMenuItems() {
  const session = useSession();
  const pathname = usePathname();
  const item = "block w-full rounded px-3 py-2 text-left text-sm font-medium hover:bg-raised";

  if (session.status !== "signed-in") {
    return (
      <Link href={signInHref(pathname)} className={item}>
        Sign in
      </Link>
    );
  }
  return (
    <>
      <Link href="/account" className={item}>
        Account
      </Link>
      <button type="button" className={item} onClick={() => void session.signOut()}>
        Sign out
      </button>
    </>
  );
}
