import type { Metadata } from "next";
import { SignUpCard } from "@/components/auth/sign-up-card";
import { safeNextPath } from "@/lib/auth/next-path";
import { firstParam, providerConfig } from "@/lib/auth/providers";

export const metadata: Metadata = { title: "Create an account", robots: { index: false } };

export default async function Page({
  searchParams,
}: {
  searchParams: Promise<{ next?: string | string[] }>;
}) {
  const next = safeNextPath(firstParam((await searchParams).next));
  return <SignUpCard providers={await providerConfig()} next={next} />;
}
