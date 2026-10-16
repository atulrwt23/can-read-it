import type { Metadata } from "next";
import { ChapterEditor } from "@/components/admin/chapter-editor";
import { AdminShell } from "@/components/admin/ui";

export const metadata: Metadata = { title: "Edit chapter", robots: { index: false } };

export default async function Page({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return (
    <AdminShell title="Edit chapter" back={{ href: "/admin", label: "Catalog" }}>
      <ChapterEditor id={id} />
    </AdminShell>
  );
}
