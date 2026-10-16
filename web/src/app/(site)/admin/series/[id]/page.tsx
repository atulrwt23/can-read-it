import type { Metadata } from "next";
import { SeriesEditor } from "@/components/admin/series-editor";
import { AdminShell } from "@/components/admin/ui";

export const metadata: Metadata = { title: "Edit series", robots: { index: false } };

export default async function Page({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return (
    <AdminShell title="Edit series" back={{ href: "/admin", label: "Catalog" }}>
      <SeriesEditor id={id} />
    </AdminShell>
  );
}
