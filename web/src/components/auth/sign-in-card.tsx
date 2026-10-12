"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { type FormEvent, useState } from "react";
import { auth, errorMessage, type Me } from "@/lib/auth/client";
import { CodeStep } from "./code-step";
import { Button, Card, Divider, Notice, TextField } from "./form";
import { useSession } from "./session";
import { type ProviderConfig, SocialButtons } from "./social-buttons";

export type Method = "code" | "password";

/** Sign in with Google, Apple, an emailed code or a password (CLAUDE.md section 8, auth pages). */
export function SignInCard({ providers, next }: { providers: ProviderConfig; next: string }) {
  const router = useRouter();
  const session = useSession();
  const [method, setMethod] = useState<Method>("code");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [codeSentTo, setCodeSentTo] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function done(me: Me) {
    session.signedIn(me);
    router.push(next);
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      if (method === "password") {
        done(await auth.login(email, password));
      } else {
        await auth.startCode(email);
        setCodeSentTo(email);
        setBusy(false);
      }
    } catch (caught) {
      setError(errorMessage(caught));
      setBusy(false);
    }
  }

  if (codeSentTo) {
    return (
      <Card title="Check your email">
        <CodeStep
          email={codeSentTo}
          intro="We sent a sign-in code to"
          submitLabel="Sign in"
          onSubmit={async (code) => done(await auth.verifyCode(codeSentTo, code))}
          onResend={() => auth.startCode(codeSentTo)}
        />
        <button
          type="button"
          onClick={() => setCodeSentTo(null)}
          className="self-start text-sm text-link underline"
        >
          Use a different email
        </button>
      </Card>
    );
  }

  return (
    <Card title="Sign in">
      <SocialButtons
        providers={providers}
        onToken={async (provider, idToken, nonce) =>
          done(await auth.oauth(provider, idToken, nonce))
        }
      />
      {providers.google || providers.apple ? <Divider label="or" /> : null}
      <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
        {error ? <Notice kind="error">{error}</Notice> : null}
        <MethodChoice method={method} onChange={setMethod} />
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
        {method === "password" ? (
          <TextField
            id="password"
            label="Password"
            type="password"
            name="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            hint={
              <Link href="/login/reset" className="text-link underline">
                Forgot your password?
              </Link>
            }
          />
        ) : null}
        <Button busy={busy}>{method === "password" ? "Sign in" : "Email me a code"}</Button>
      </form>
      <p className="text-sm text-muted">
        New here?{" "}
        <Link
          href={`/signup${next === "/" ? "" : `?next=${encodeURIComponent(next)}`}`}
          className="text-link underline"
        >
          Create an account
        </Link>
        . Reading never needs one.
      </p>
    </Card>
  );
}

export function MethodChoice({
  method,
  onChange,
  legend = "How do you want to sign in?",
}: {
  method: Method;
  onChange(method: Method): void;
  legend?: string;
}) {
  return (
    <fieldset className="flex gap-2">
      <legend className="sr-only">{legend}</legend>
      {(
        [
          ["code", "With a code"],
          ["password", "With a password"],
        ] as const
      ).map(([value, label]) => (
        <label
          key={value}
          className="flex flex-1 cursor-pointer items-center justify-center rounded border border-line px-3 py-2 text-sm font-semibold has-checked:border-primary-text has-checked:bg-raised has-focus-visible:outline-2 has-focus-visible:outline-primary-text"
        >
          <input
            type="radio"
            name="method"
            value={value}
            checked={method === value}
            onChange={() => onChange(value)}
            className="sr-only"
          />
          {label}
        </label>
      ))}
    </fieldset>
  );
}
