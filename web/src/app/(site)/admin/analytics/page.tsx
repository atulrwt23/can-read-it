import type { Metadata } from "next";
import { AnalyticsView } from "@/components/admin/analytics-view";
import { AdminShell } from "@/components/admin/ui";

export const metadata: Metadata = { title: "Analytics", robots: { index: false } };

export default function Page() {
  return (
    <AdminShell title="Analytics" back={{ href: "/admin", label: "Catalog" }}>
      <AnalyticsView />
    </AdminShell>
  );
}
