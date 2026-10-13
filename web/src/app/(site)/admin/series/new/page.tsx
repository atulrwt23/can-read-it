import type { Metadata } from "next";
import { SeriesEditor } from "@/components/admin/series-editor";
import { AdminShell } from "@/components/admin/ui";

export const metadata: Metadata = { title: "New series", robots: { index: false } };

export default function Page() {
  return (
    <AdminShell title="New series" back={{ href: "/admin", label: "Catalog" }}>
      <SeriesEditor />
    </AdminShell>
  );
}
