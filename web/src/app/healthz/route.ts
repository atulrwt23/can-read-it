/** Liveness for the container health check and deploy smoke test. Does not call the API. */
export const dynamic = "force-dynamic";

export function GET() {
  return new Response("ok", { headers: { "Cache-Control": "no-store" } });
}
