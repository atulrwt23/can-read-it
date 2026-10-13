/**
 * Where to go after signing in: the `next` query parameter if it is a path on this site, otherwise home. Anything
 * else (another origin, `//host`, `/\host`, a scheme) could send the reader to a phishing page.
 */
export function safeNextPath(value: string | null | undefined): string {
  if (!value?.startsWith("/") || value.startsWith("//") || value.startsWith("/\\")) {
    return "/";
  }
  if ([...value].some((char) => char.charCodeAt(0) < 0x20)) {
    return "/";
  }
  // The auth pages themselves would loop.
  if (/^\/(login|signup)(\/|\?|$)/.test(value)) {
    return "/";
  }
  return value;
}
