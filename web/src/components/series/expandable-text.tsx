"use client";

import { useId, useState } from "react";

const LONG = 280;

/** Clamps long text to a few lines with a "Show more" toggle. */
export function ExpandableText({ text }: { text: string }) {
  const id = useId();
  const [expanded, setExpanded] = useState(false);
  const long = text.length > LONG;
  return (
    <div>
      <p
        id={id}
        className={`max-w-prose whitespace-pre-line text-muted ${long && !expanded ? "line-clamp-4" : ""}`}
      >
        {text}
      </p>
      {long && (
        <button
          type="button"
          aria-expanded={expanded}
          aria-controls={id}
          onClick={() => setExpanded(!expanded)}
          className="mt-1 text-sm font-semibold text-primary-text hover:underline"
        >
          {expanded ? "Show less" : "Show more"}
        </button>
      )}
    </div>
  );
}
