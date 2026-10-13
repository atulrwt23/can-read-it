import type { ComponentProps, ReactNode } from "react";

/** A labelled input. The hint and the error are announced with the field (aria-describedby). */
export function TextField({
  id,
  label,
  hint,
  error,
  ...input
}: {
  id: string;
  label: string;
  hint?: ReactNode;
  error?: string | null;
} & ComponentProps<"input">) {
  const describedBy = [hint ? `${id}-hint` : null, error ? `${id}-error` : null]
    .filter(Boolean)
    .join(" ");
  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={id} className="text-sm font-semibold">
        {label}
      </label>
      <input
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy || undefined}
        className="h-10 rounded border border-line bg-surface px-3 text-base placeholder:text-muted focus:border-primary-text focus:outline-none aria-[invalid=true]:border-text"
        {...input}
      />
      {hint ? (
        <p id={`${id}-hint`} className="text-sm text-muted">
          {hint}
        </p>
      ) : null}
      {error ? (
        <p id={`${id}-error`} className="text-sm font-semibold">
          {error}
        </p>
      ) : null}
    </div>
  );
}

const BUTTON = {
  primary: "bg-primary text-on-primary hover:brightness-95",
  secondary: "border border-line bg-surface text-text hover:bg-raised",
  // TODO(product): the design tokens have no danger colour; destructive buttons are outlined and labelled plainly.
  danger: "border-2 border-text bg-surface text-text hover:bg-raised",
} as const;

export function Button({
  variant = "primary",
  busy = false,
  className = "",
  children,
  ...button
}: { variant?: keyof typeof BUTTON; busy?: boolean } & ComponentProps<"button">) {
  return (
    <button
      type="submit"
      aria-busy={busy || undefined}
      disabled={busy || button.disabled}
      className={`inline-flex h-10 items-center justify-center gap-2 rounded px-4 text-sm font-semibold disabled:cursor-not-allowed disabled:opacity-60 ${BUTTON[variant]} ${className}`}
      {...button}
    >
      {children}
    </button>
  );
}

/** A message about the last action. Errors interrupt (role=alert); confirmations wait their turn (role=status). */
export function Notice({ kind, children }: { kind: "error" | "info"; children: ReactNode }) {
  return (
    <div
      role={kind === "error" ? "alert" : "status"}
      className="flex gap-2 rounded border border-line bg-raised px-3 py-2 text-sm"
    >
      <span aria-hidden="true" className="font-bold">
        {kind === "error" ? "!" : "i"}
      </span>
      <span>{children}</span>
    </div>
  );
}

export function Card({ title, children }: { title: string; children: ReactNode }) {
  return (
    <main className="mx-auto max-w-md px-4 py-12">
      <div className="flex flex-col gap-6 rounded-md bg-surface p-6 shadow-card">
        <h1 className="text-2xl">{title}</h1>
        {children}
      </div>
    </main>
  );
}

export function Divider({ label }: { label: string }) {
  return (
    <div className="flex items-center gap-3 text-sm text-muted">
      <span aria-hidden="true" className="h-px flex-1 bg-line" />
      {label}
      <span aria-hidden="true" className="h-px flex-1 bg-line" />
    </div>
  );
}
