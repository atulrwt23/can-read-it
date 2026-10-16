import type { Metadata } from "next";
import { LibraryView } from "@/components/library/library-view";

export const metadata: Metadata = { title: "Library", robots: { index: false } };

export default function Page() {
  return <LibraryView />;
}
