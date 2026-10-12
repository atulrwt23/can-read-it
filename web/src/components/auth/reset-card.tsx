"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { type FormEvent, useState } from "react";
import { ApiError, auth, errorMessage } from "@/lib/auth/client";
import { CodeStep } from "./code-step";
import { Button, Card, Notice, TextField } from "./form";
import { useSession } from "./session";

/** Forgot password: an emailed code, then a new password. Every other device is signed out. */
export function ResetCard() {
  const router = useRouter();
  const session = useSession();
  const [email, setEmail] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [sent, setSent] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await auth.forgotPassword(email);
      setSent(true);
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setBusy(false);
    }
  }

  if (sent) {
    return (
      <Card title="Choose a new password">
        <CodeStep
          email={email}
          intro="If this email has an account, we sent a reset code to"
          submitLabel="Save password and sign in"
          onSubmit={async (code) => {
            if (newPassword.length < 10) {
              throw new ApiError(
                400,
                "too_short",
                "Your new password needs at least 10 characters.",
              );
            }
            session.signedIn(await auth.resetPassword(email, code, newPassword));
            router.push("/account");
          }}
          onResend={() => auth.forgotPassword(email)}
        >
          <TextField
            id="new-password"
            label="New password"
            type="password"
            autoComplete="new-password"
            required
            minLength={10}
            value={newPassword}
            onChange={(event) => setNewPassword(event.target.value)}
            hint="At least 10 characters. Every other device will be signed out."
          />
        </CodeStep>
      </Card>
    );
  }

  return (
    <Card title="Reset your password">
      <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
        {error ? <Notice kind="error">{error}</Notice> : null}
        <p className="text-muted">We'll email you a code to choose a new password.</p>
        <TextField
          id="email"
          label="Email"
          type="email"
          autoComplete="email"
          required
          value={email}
          onChange={(event) => setEmail(event.target.value)}
        />
        <Button busy={busy}>Email me a code</Button>
      </form>
      <Link href="/login" className="self-start text-sm text-link underline">
        Back to sign in
      </Link>
    </Card>
  );
}
