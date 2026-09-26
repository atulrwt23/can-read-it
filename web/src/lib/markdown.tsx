import { Fragment, type ReactNode } from "react";

/**
 * Renders the small Markdown subset novel chapters use: paragraphs, #/## headings, scene breaks
 * (--- or ***), **strong** and *emphasis* / _emphasis_. Output is React elements only, so author
 * text can never inject HTML.
 */
export function Markdown({ source }: { source: string }) {
  const blocks = source.replace(/\r\n?/g, "\n").split(/\n{2,}/);
  return (
    <>
      {blocks.map((block, index) => {
        const text = block.trim();
        const key = `${index}`;
        if (!text) return null;
        if (/^(-{3,}|\*{3,})$/.test(text)) return <hr key={key} />;
        const heading = /^(#{1,2})\s+(.*)$/.exec(text);
        if (heading?.[1] && heading[2] !== undefined) {
          return heading[1].length === 1 ? (
            <h2 key={key}>{inline(heading[2])}</h2>
          ) : (
            <h3 key={key}>{inline(heading[2])}</h3>
          );
        }
        return <p key={key}>{inline(text.replace(/\n/g, " "))}</p>;
      })}
    </>
  );
}

const INLINE = /(\*\*[^*]+\*\*|\*[^*\s][^*]*\*|_[^_\s][^_]*_)/g;

export function inline(text: string): ReactNode {
  return text.split(INLINE).map((part, index) => {
    const key = `${index}`;
    if (part.startsWith("**") && part.endsWith("**") && part.length > 4) {
      return <strong key={key}>{part.slice(2, -2)}</strong>;
    }
    if (
      (part.startsWith("*") && part.endsWith("*")) ||
      (part.startsWith("_") && part.endsWith("_"))
    ) {
      if (part.length > 2) return <em key={key}>{part.slice(1, -1)}</em>;
    }
    return <Fragment key={key}>{part}</Fragment>;
  });
}
