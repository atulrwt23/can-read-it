"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const NAV = [
  { href: "/", label: "Home" },
  { href: "/browse", label: "Browse" },
  { href: "/library", label: "Library" },
];

/** Home matches only itself; other sections also match their sub-pages. */
export function isCurrent(href: string, pathname: string): boolean {
  return href === "/" ? pathname === "/" : pathname === href || pathname.startsWith(`${href}/`);
}

/**
 * The primary navigation links, with the current section highlighted (`aria-current="page"`). It depends only on
 * the URL, so pages stay the same for every visitor.
 */
export function NavLinks({ variant }: { variant: "bar" | "menu" }) {
  const pathname = usePathname();
  return NAV.map((item) => {
    const current = isCurrent(item.href, pathname);
    return (
      <li key={item.href}>
        <Link
          href={item.href}
          aria-current={current ? "page" : undefined}
          className={
            variant === "bar"
              ? "block rounded px-3 py-2 text-sm font-medium text-muted hover:bg-raised hover:text-text aria-[current=page]:bg-raised aria-[current=page]:font-semibold aria-[current=page]:text-text aria-[current=page]:shadow-[inset_0_-2px_0_var(--primary)]"
              : "block rounded px-3 py-2 text-sm font-medium hover:bg-raised aria-[current=page]:bg-raised aria-[current=page]:font-semibold aria-[current=page]:shadow-[inset_2px_0_0_var(--primary)]"
          }
        >
          {item.label}
        </Link>
      </li>
    );
  });
}
