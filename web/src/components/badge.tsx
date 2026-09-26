import type { ReactNode } from "react";

type Tone = "neutral" | "primary" | "success" | "link" | "warning";

const TONES: Record<Tone, string> = {
  neutral: "bg-raised text-muted",
  primary: "bg-primary text-on-primary",
  success: "bg-raised text-success",
  link: "bg-link text-white dark:text-bg",
  warning: "bg-raised text-star",
};

export function Badge({ tone = "neutral", children }: { tone?: Tone; children: ReactNode }) {
  return (
    <span
      className={`inline-flex items-center rounded px-1.5 py-0.5 text-xs font-semibold leading-4 ${TONES[tone]}`}
    >
      {children}
    </span>
  );
}
