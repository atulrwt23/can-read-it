import type { Metadata } from "next";
import { ResetCard } from "@/components/auth/reset-card";

export const metadata: Metadata = { title: "Reset your password", robots: { index: false } };

export default function Page() {
  return <ResetCard />;
}
