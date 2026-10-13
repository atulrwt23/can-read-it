import type { Metadata } from "next";
import { AccountView } from "@/components/account/account-view";
import { providerConfig } from "@/lib/auth/providers";

export const metadata: Metadata = { title: "Account", robots: { index: false } };

export default async function Page() {
  return <AccountView providers={await providerConfig()} />;
}
