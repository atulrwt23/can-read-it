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
        <Link href="/admin/series/new" className={linkButton}>
          New series
        </Link>
      }
    >
      <SeriesList />
    </AdminShell>
  );
}
