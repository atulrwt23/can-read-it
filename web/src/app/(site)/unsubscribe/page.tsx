import type { Metadata } from "next";
import { UnsubscribeCard } from "@/components/notifications/unsubscribe-card";

export const metadata: Metadata = {
  title: "Stop new-chapter emails",
  robots: { index: false },
  // The link's token must not leak to other sites through the Referer header.
  referrer: "no-referrer",
};

type Props = { searchParams: Promise<Record<string, string | string[] | undefined>> };

/** The page an email's unsubscribe link opens. Nothing changes until the reader presses the button. */
export default async function Page({ searchParams }: Props) {
  const { token } = await searchParams;
  return <UnsubscribeCard token={typeof token === "string" ? token : null} />;
}
