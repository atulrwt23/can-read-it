"use client";

import { type FormEvent, useState } from "react";
import { errorMessage } from "@/lib/auth/client";
import { Button, Notice, TextField } from "./form";

/**
 * The second step of every emailed-code flow: "we sent a code to …", a 6-digit field, and a way to ask again.
 * `onSubmit` throws to show an error.
 */
export function CodeStep({
  email,
  intro,
  submitLabel,
  onSubmit,
  onResend,
  children,
}: {
  email: string;
  intro: string;
  submitLabel: string;
  onSubmit(code: string): Promise<void>;
  onResend?(): Promise<void>;
  children?: React.ReactNode;
}) {
  const [code, setCode] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [resent, setResent] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await onSubmit(code);
    } catch (caught) {
      setError(errorMessage(caught));
      setBusy(false);
    }
  }

  async function resend() {
    if (!onResend) return;
    setError(null);
    try {
      await onResend();
      setResent(true);
    } catch (caught) {
      setError(errorMessage(caught));
    }
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
      <p>
        {intro} <strong className="break-all">{email}</strong>. It works for 10 minutes.
      </p>
      {error ? <Notice kind="error">{error}</Notice> : null}
      {resent ? <Notice kind="info">We sent a new code.</Notice> : null}
      <TextField
        id="code"
        label="6-digit code"
        name="code"
        inputMode="numeric"
        autoComplete="one-time-code"
        pattern="[0-9]{6}"
        maxLength={6}
        required
        value={code}
        onChange={(event) => setCode(event.target.value.replace(/\D/g, ""))}
      />
      {children}
      <Button busy={busy} disabled={code.length !== 6}>
        {submitLabel}
      </Button>
      {onResend ? (
        <button type="button" onClick={resend} className="self-start text-sm text-link underline">
          Send a new code
        </button>
      ) : null}
    </form>
  );
}
