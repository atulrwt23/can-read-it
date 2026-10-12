import { afterEach, describe, expect, it } from "vitest";
import { canonical, jsonLd, siteUrl } from "./site";

describe("site URL", () => {
  afterEach(() => {
    delete process.env.SITE_URL;
  });

  it("uses only the origin of a valid http(s) SITE_URL", () => {
    process.env.SITE_URL = "https://read.example/some/path?x=1";
    expect(siteUrl()?.href).toBe("https://read.example/");
    process.env.SITE_URL = "ftp://read.example";
    expect(siteUrl()).toBeUndefined();
    process.env.SITE_URL = "not a url";
    expect(siteUrl()).toBeUndefined();
  });

  it("leaves canonical URLs out without a known origin", () => {
    expect(canonical("/series/x")).toEqual({});
    process.env.SITE_URL = "https://read.example";
    expect(canonical("/series/x")).toEqual({
      alternates: { canonical: "/series/x" },
      openGraph: { url: "/series/x" },
    });
  });

  it("keeps JSON-LD from closing its script tag", () => {
    expect(jsonLd({ name: "</script><b>" })).toBe('{"name":"\\u003c/script>\\u003cb>"}');
  });
});
