"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { type FormEvent, useState } from "react";
import { auth, errorMessage, type Me } from "@/lib/auth/client";
import { CodeStep } from "./code-step";
import { Button, Card, Divider, Notice, TextField } from "./form";
import { useSession } from "./session";
import { type Method, MethodChoice } from "./sign-in-card";
import { type ProviderConfig, SocialButtons } from "./social-buttons";

const HANDLE = /^[A-Za-z0-9_]{3,30}$/;

/**
 * Create an account: with Google or Apple, with an emailed code, or with a password. Every email path ends with a
 * code from the inbox. The API answers the same whether or not the email already has an account, and so does this
 * page; an existing owner gets an email instead and signs in with that code.
 */
export function SignUpCard({ providers, next }: { providers: ProviderConfig; next: string }) {
  const router = useRouter();
  const session = useSession();
  const [method, setMethod] = useState<Method>("code");
  const [email, setEmail] = useState("");
  const [handle, setHandle] = useState("");
  const [password, setPassword] = useState("");
  const [step, setStep] = useState<"form" | "code">("form");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function done(me: Me) {
    session.signedIn(me);
    router.push(next);
  }

  const handleError =
    handle && !HANDLE.test(handle) ? "3 to 30 letters, digits or underscores." : null;
  const passwordError =
    method === "password" && password && password.length < 10 ? "At least 10 characters." : null;

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!HANDLE.test(handle) || passwordError) {
      setError("Check the highlighted fields.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      if (method === "password") {
        await auth.register(email, password, handle);
      } else {
        await auth.startCode(email);
      }
      setStep("code");
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setBusy(false);
    }
  }

  if (step === "code") {
    return (
      <Card title="Check your email">
        <CodeStep
          email={email}
          intro="We sent a code to"
          submitLabel={method === "password" ? "Confirm and sign in" : "Create account"}
          onSubmit={async (code) => done(await auth.verifyCode(email, code, handle))}
          onResend={() => auth.startCode(email)}
        >
          {method === "code" ? (
            <TextField
              id="code-handle"
              label="Handle"
              autoComplete="username"
              value={handle}
              onChange={(event) => setHandle(event.target.value)}
              hint="Shown with your comments later. You can change it any time."
            />
          ) : null}
        </CodeStep>
        {method === "password" ? (
          <p className="text-sm text-muted">
            No email yet? You can already{" "}
            <Link href="/login" className="text-link underline">
              sign in with your password
            </Link>{" "}
            and confirm later.
          </p>
        ) : null}
      </Card>
    );
  }

  return (
    <Card title="Create an account">
      <SocialButtons
        providers={providers}
        onToken={async (provider, idToken, nonce) =>
          done(await auth.oauth(provider, idToken, nonce))
        }
      />
      {providers.google || providers.apple ? <Divider label="or" /> : null}
      <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
        {error ? <Notice kind="error">{error}</Notice> : null}
        <MethodChoice
          method={method}
          onChange={setMethod}
          legend="How do you want to sign in later?"
        />
        <TextField
          id="email"
          label="Email"
          type="email"
          name="email"
          autoComplete="email"
          required
          value={email}
          onChange={(event) => setEmail(event.target.value)}
        />
        <TextField
          id="handle"
          label="Handle"
          name="handle"
          autoComplete="username"
          required
          value={handle}
          onChange={(event) => setHandle(event.target.value)}
          hint="3 to 30 letters, digits or underscores. You can change it later."
          error={handleError}
        />
        {method === "password" ? (
          <TextField
            id="password"
            label="Password"
            type="password"
            name="password"
            autoComplete="new-password"
            required
            minLength={10}
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            hint="At least 10 characters. A phrase is easiest to remember."
            error={passwordError}
          />
        ) : null}
        <Button busy={busy}>{method === "password" ? "Create account" : "Email me a code"}</Button>
      </form>
      <p className="text-sm text-muted">
        Already have an account?{" "}
        <Link href="/login" className="text-link underline">
          Sign in
        </Link>
        .
      </p>
    </Card>
  );
}
