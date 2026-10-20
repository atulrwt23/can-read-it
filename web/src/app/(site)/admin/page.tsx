import type { Metadata } from "next";
import Link from "next/link";
import { SeriesList } from "@/components/admin/series-list";
import { AdminShell, linkButton } from "@/components/admin/ui";

export const metadata: Metadata = { title: "Catalog", robots: { index: false } };

export default function Page() {
  return (
    <AdminShell
      title="Catalog"
      actions={
        <div className="flex flex-wrap items-center gap-3">
          <Link href="/admin/moderation" className="text-sm font-semibold text-link underline">
            Moderation
          </Link>
          <Link href="/admin/analytics" className="text-sm font-semibold text-link underline">
            Analytics
          </Link>
          <Link href="/admin/series/new" className={linkButton}>
            New series
          </Link>
        </div>
      }
    >
      <SeriesList />
    </AdminShell>
  );
}
