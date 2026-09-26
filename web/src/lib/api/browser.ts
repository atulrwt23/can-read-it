import createClient from "openapi-fetch";
import type { paths } from "./schema";

/** Browser-side client: always same-origin /api/*, so no CORS and first-party cookies. */
export const browserApi = createClient<paths>({ baseUrl: "" });
