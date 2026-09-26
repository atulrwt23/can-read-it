import Link from "next/link";
import { BRAND_NAME } from "@/lib/brand";
import { SearchBox } from "./search-box";
import { ThemeToggle } from "./theme-toggle";

const NAV = [
  { href: "/", label: "Home" },
  { href: "/browse", label: "Browse" },
  { href: "/library", label: "Library" },
];

export function SiteHeader() {
  return (
    <header className="sticky top-0 z-20 border-b border-line bg-surface/95 backdrop-blur supports-[backdrop-filter]:bg-surface/80">
      <div className="mx-auto flex h-14 max-w-6xl items-center gap-4 px-4">
        <Link href="/" className="text-lg font-extrabold tracking-tight">
          <span className="text-primary-text">Can</span>ReadIt
          <span className="sr-only"> {BRAND_NAME} home</span>
        </Link>
        <nav aria-label="Primary" className="hidden md:block">
          <ul className="flex gap-1">
            {NAV.map((item) => (
              <li key={item.href}>
                <Link
                  href={item.href}
                  className="rounded px-3 py-2 text-sm font-medium text-muted hover:bg-raised hover:text-text"
                >
                  {item.label}
                </Link>
              </li>
            ))}
          </ul>
        </nav>
        <SearchBox className="ml-auto hidden w-72 sm:block" />
        <div className="ml-auto flex items-center gap-1 sm:ml-0">
          <Link
            href="/browse"
            aria-label="Search"
            className="inline-flex size-9 items-center justify-center rounded text-muted hover:bg-raised sm:hidden"
          >
            <svg
              aria-hidden="true"
              viewBox="0 0 24 24"
              className="size-5"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
            >
              <path d="m21 21-4.3-4.3M11 18a7 7 0 1 1 0-14 7 7 0 0 1 0 14Z" />
            </svg>
          </Link>
          <ThemeToggle />
          <Link
            href="/login"
            className="hidden rounded bg-primary px-3 py-1.5 text-sm font-semibold text-on-primary hover:brightness-95 md:inline-block"
          >
            Sign in
          </Link>
          <button
            type="button"
            popoverTarget="mobile-menu"
            aria-label="Open menu"
            className="inline-flex size-9 items-center justify-center rounded text-muted hover:bg-raised md:hidden"
          >
            <svg
              aria-hidden="true"
              viewBox="0 0 24 24"
              className="size-5"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
            >
              <path d="M4 6h16M4 12h16M4 18h16" />
            </svg>
          </button>
        </div>
      </div>
      <nav
        id="mobile-menu"
        popover="auto"
        aria-label="Menu"
        className="m-0 mt-14 ml-auto w-56 rounded-md border border-line bg-surface p-2 text-text shadow-card"
      >
        <ul>
          {[...NAV, { href: "/login", label: "Sign in" }].map((item) => (
            <li key={item.href}>
              <Link
                href={item.href}
                className="block rounded px-3 py-2 text-sm font-medium hover:bg-raised"
              >
                {item.label}
              </Link>
            </li>
          ))}
        </ul>
      </nav>
    </header>
  );
}
