import { connection } from "next/server";
import type { ProviderConfig } from "@/components/auth/social-buttons";

/**
 * The web client IDs of the configured social sign-in providers: the first entry of `GOOGLE_CLIENT_IDS` and
 * `APPLE_CLIENT_IDS`, the same settings the API checks tokens against (docs/deploy.md). Read at request time, so
 * one image works with any settings. A provider without one shows no button.
 */
export async function providerConfig(): Promise<ProviderConfig> {
  await connection();
  return {
    google: firstClientId(process.env.GOOGLE_CLIENT_IDS),
    apple: firstClientId(process.env.APPLE_CLIENT_IDS),
  };
}

export function firstClientId(list: string | undefined): string | undefined {
  return list
    ?.split(",")
    .map((id) => id.trim())
    .find(Boolean);
}

/** The first value of a query parameter. */
export function firstParam(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}
