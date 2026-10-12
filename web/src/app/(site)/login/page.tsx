import type { Metadata } from "next";
import { SignInCard } from "@/components/auth/sign-in-card";
import { safeNextPath } from "@/lib/auth/next-path";
import { firstParam, providerConfig } from "@/lib/auth/providers";

export const metadata: Metadata = { title: "Sign in", robots: { index: false } };

export default async function Page({
  searchParams,
}: {
  searchParams: Promise<{ next?: string | string[] }>;
}) {
  const next = safeNextPath(firstParam((await searchParams).next));
  return <SignInCard providers={await providerConfig()} next={next} />;
}
