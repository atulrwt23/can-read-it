import type { components } from "@/lib/api/schema";

type Schemas = components["schemas"];
export type Me = Schemas["MeResponse"];
export type SessionView = Schemas["SessionView"];
export type Provider = "google" | "apple";

/** An API error answer (RFC 9457 problem details): the HTTP status and the stable `code`. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
  ) {
    super(message);
  }
}

const CSRF_COOKIE = "XSRF-TOKEN";
const CSRF_HEADER = "X-XSRF-TOKEN";

/**
 * A hint, in localStorage, that this browser has signed in. The session cookies are httpOnly, so scripts can't
 * see them; the hint lets signed-out visitors skip the /me request entirely. It is only a hint: the API decides.
 */
const HINT_KEY = "signed-in";

export function hasSignedInHint(): boolean {
  try {
    return localStorage.getItem(HINT_KEY) === "1";
  } catch {
    return false;
  }
}

export function setSignedInHint(signedIn: boolean) {
  try {
    if (signedIn) localStorage.setItem(HINT_KEY, "1");
    else localStorage.removeItem(HINT_KEY);
  } catch {
    // Storage unavailable: the account menu then checks with the API on each page.
  }
}

function readCookie(name: string): string | null {
  for (const part of document.cookie.split("; ")) {
    const [key, ...rest] = part.split("=");
    if (key === name) return decodeURIComponent(rest.join("="));
  }
  return null;
}

/** Writes need the CSRF cookie echoed in a header. Any non-public API response hands the cookie out. */
async function csrfToken(): Promise<string | null> {
  const existing = readCookie(CSRF_COOKIE);
  if (existing) return existing;
  await fetch("/api/v1/me", { credentials: "same-origin" });
  return readCookie(CSRF_COOKIE);
}

let refreshing: Promise<boolean> | null = null;

/**
 * Swaps the refresh cookie for new session cookies. Concurrent callers share one attempt. Another tab refreshing
 * at the same moment answers 409; its new cookies are already ours, so that counts as success.
 */
export function refreshSession(): Promise<boolean> {
  refreshing ??= fetch("/api/v1/auth/refresh", { method: "POST", credentials: "same-origin" })
    .then((response) => response.ok || response.status === 409)
    .catch(() => false)
    .finally(() => {
      refreshing = null;
    });
  return refreshing;
}

type Method = "GET" | "POST" | "PUT" | "PATCH" | "DELETE";

/**
 * Calls the API from the browser (same-origin /api/*, first-party cookies). Writes carry the CSRF header. When the
 * access token has expired (401 outside /auth), it refreshes the session once and retries. A `FormData` body is
 * sent as a multipart upload; anything else as JSON. `keepalive` lets a request finish after the page closes.
 */
export async function api<T = void>(
  method: Method,
  path: string,
  body?: unknown,
  options: { keepalive?: boolean } = {},
): Promise<T> {
  const send = async () => {
    const headers: Record<string, string> = { Accept: "application/json" };
    const multipart = body instanceof FormData;
    if (body !== undefined && !multipart) headers["Content-Type"] = "application/json";
    if (method !== "GET") {
      const token = await csrfToken();
      if (token) headers[CSRF_HEADER] = token;
    }
    return fetch(path, {
      method,
      headers,
      credentials: "same-origin",
      body: body === undefined ? undefined : multipart ? body : JSON.stringify(body),
      keepalive: options.keepalive,
    });
  };

  let response = await send();
  if (response.status === 401 && !path.startsWith("/api/v1/auth/") && (await refreshSession())) {
    response = await send();
  }
  if (!response.ok) {
    throw await toError(response);
  }
  if (response.status === 204 || response.status === 202) {
    const text = await response.text();
    return (text ? JSON.parse(text) : undefined) as T;
  }
  return (await response.json()) as T;
}

async function toError(response: Response): Promise<ApiError> {
  try {
    const problem = (await response.json()) as { code?: string; detail?: string; title?: string };
    return new ApiError(
      response.status,
      problem.code ?? "error",
      problem.detail ?? problem.title ?? "Something went wrong.",
    );
  } catch {
    return new ApiError(response.status, "error", "Something went wrong. Try again.");
  }
}

/** A message for a failed request, preferring the API's own wording. */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 429) return "Too many attempts. Wait a little and try again.";
    return error.message;
  }
  return "Something went wrong. Check your connection and try again.";
}

// --- Endpoints

export const auth = {
  me: () => api<Me>("GET", "/api/v1/me"),
  login: (email: string, password: string) =>
    api<Me>("POST", "/api/v1/auth/password/login", { email, password }),
  register: (email: string, password: string, handle: string, botToken?: string) =>
    api("POST", "/api/v1/auth/password/register", { email, password, handle, botToken }),
  startCode: (email: string, botToken?: string) =>
    api("POST", "/api/v1/auth/email/start", { email, botToken }),
  verifyCode: (email: string, code: string, handle?: string) =>
    api<Me>("POST", "/api/v1/auth/email/verify", { email, code, handle: handle || undefined }),
  forgotPassword: (email: string, botToken?: string) =>
    api("POST", "/api/v1/auth/password/forgot", { email, botToken }),
  resetPassword: (email: string, code: string, newPassword: string) =>
    api<Me>("POST", "/api/v1/auth/password/reset", { email, code, newPassword }),
  nonce: () => api<{ nonce: string }>("POST", "/api/v1/auth/oauth/nonce"),
  oauth: (provider: Provider, idToken: string, nonce: string) =>
    api<Me>("POST", `/api/v1/auth/oauth/${provider}`, { idToken, nonce }),
  logout: () => api("POST", "/api/v1/auth/logout"),
};

export const account = {
  update: (changes: { handle?: string; displayName?: string }) =>
    api<Me>("PATCH", "/api/v1/me", changes),
  changePassword: (newPassword: string, currentPassword?: string) =>
    api("POST", "/api/v1/me/password", { currentPassword, newPassword }),
  resendConfirmation: () => api("POST", "/api/v1/me/email/verification"),
  sessions: () => api<SessionView[]>("GET", "/api/v1/me/sessions"),
  revokeSession: (id: string) => api("DELETE", `/api/v1/me/sessions/${id}`),
  revokeOtherSessions: () => api("POST", "/api/v1/me/sessions/revoke-all"),
  link: (provider: Provider, idToken: string, nonce: string) =>
    api("POST", `/api/v1/me/identities/${provider}`, { idToken, nonce }),
  unlink: (provider: Provider) => api("DELETE", `/api/v1/me/identities/${provider}`),
  delete: (currentPassword?: string) =>
    api<{ eraseAfter: string }>("DELETE", "/api/v1/me", { currentPassword }),
};
