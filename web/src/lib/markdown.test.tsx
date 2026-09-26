import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { Markdown } from "./markdown";

const html = (source: string) => renderToStaticMarkup(<Markdown source={source} />);

describe("Markdown", () => {
  it("renders paragraphs, headings and scene breaks", () => {
    expect(html("# Part One\n\nFirst line\nsame paragraph.\n\n---\n\n## Later")).toBe(
      "<h2>Part One</h2><p>First line same paragraph.</p><hr/><h3>Later</h3>",
    );
  });

  it("renders emphasis and strong text", () => {
    expect(html("A *quiet* night, **very** _still_.")).toBe(
      "<p>A <em>quiet</em> night, <strong>very</strong> <em>still</em>.</p>",
    );
  });

  it("never renders author HTML", () => {
    expect(html('<img src=x onerror="alert(1)"> and <script>alert(1)</script>')).toBe(
      "<p>&lt;img src=x onerror=&quot;alert(1)&quot;&gt; and &lt;script&gt;alert(1)&lt;/script&gt;</p>",
    );
  });

  it("leaves unmatched markers alone", () => {
    expect(html("2 * 3 = 6 and a lone ** here")).toBe("<p>2 * 3 = 6 and a lone ** here</p>");
  });
});
