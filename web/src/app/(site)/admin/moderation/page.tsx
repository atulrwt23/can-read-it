import type { Metadata } from "next";
import { ModerationView } from "@/components/admin/moderation-view";
import { AdminShell } from "@/components/admin/ui";

export const metadata: Metadata = { title: "Moderation", robots: { index: false } };

export default function Page() {
  return (
    <AdminShell title="Moderation" back={{ href: "/admin", label: "Catalog" }} access="moderator">
      <ModerationView />
    </AdminShell>
  );
}
