import { timingSafeEqual } from "node:crypto";
import { revalidateTag } from "next/cache";
import { parseRevalidateTags } from "@/lib/cache-tags";

/**
 * Called by the API (over the private network) when content changes, with the shared REVALIDATE_SECRET. Marks the
 * tagged data stale: the next visitor of an affected page gets it refreshed. Without a secret configured the
 * endpoint does not exist, and pages simply refresh on their timers.
 */
export async function POST(request: Request) {
  const secret = process.env.REVALIDATE_SECRET;
  if (!secret) {
    return new Response(null, { status: 404 });
  }
  if (!authorized(request.headers.get("authorization"), secret)) {
    return new Response(null, { status: 401 });
  }
  const tags = parseRevalidateTags(await request.json().catch(() => null));
  if (!tags) {
    return Response.json({ error: 'Expected {"tags": [...]}' }, { status: 400 });
  }
  for (const tag of tags) {
    revalidateTag(tag, "max");
  }
  return Response.json({ revalidated: tags });
}

function authorized(header: string | null, secret: string): boolean {
  const given = Buffer.from(header ?? "");
  const expected = Buffer.from(`Bearer ${secret}`);
  return given.length === expected.length && timingSafeEqual(given, expected);
}
