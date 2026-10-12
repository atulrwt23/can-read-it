"use client";

import Link from "next/link";
import { type FormEvent, type ReactNode, useCallback, useEffect, useState } from "react";
import { CodeStep } from "@/components/auth/code-step";
import { Button, Notice, TextField } from "@/components/auth/form";
import { useSession } from "@/components/auth/session";
import { type ProviderConfig, SocialButtons } from "@/components/auth/social-buttons";
import { Badge } from "@/components/badge";
import { Skeleton } from "@/components/skeleton";
import {
  ApiError,
  account,
  auth,
  errorMessage,
  type Me,
  type Provider,
  type SessionView,
} from "@/lib/auth/client";
import { describeDevice } from "@/lib/auth/device";
import { calendarDate, relativeTime } from "@/lib/format";

/** The account page: personal, so it renders in the browser once the session is known. */
export function AccountView({ providers }: { providers: ProviderConfig }) {
  const session = useSession();
  const [deletedUntil, setDeletedUntil] = useState<string | null>(null);

  if (deletedUntil) {
    return (
      <Shell>
        <Section title="Your account will be deleted">
          <p>
            Your account will be erased on <strong>{calendarDate(deletedUntil)}</strong>. Changed
            your mind? Sign in before then and nothing is lost.
          </p>
          <Link href="/" className="self-start text-link underline">
            Back to reading
          </Link>
        </Section>
      </Shell>
    );
  }
  if (session.status === "loading") {
    return (
      <Shell>
        <p role="status" className="sr-only">
          Loading your account…
        </p>
        <Skeleton className="h-40 w-full rounded-md" />
        <Skeleton className="h-40 w-full rounded-md" />
      </Shell>
    );
  }
  if (session.status === "signed-out") {
    return (
      <Shell>
        <Section title="Sign in to see your account">
          <p>Your profile, password and devices are here once you sign in.</p>
          <Link
            href="/login?next=/account"
            className="self-start rounded bg-primary px-4 py-2 text-sm font-semibold text-on-primary"
          >
            Sign in
          </Link>
        </Section>
      </Shell>
    );
  }
  const me = session.me;
  return (
    <Shell>
      <ProfileSection me={me} />
      <EmailSection me={me} />
      <PasswordSection me={me} />
      <MethodsSection me={me} providers={providers} />
      <SessionsSection />
      <DeleteSection me={me} onDeleted={setDeletedUntil} />
    </Shell>
  );
}

function Shell({ children }: { children: ReactNode }) {
  return (
    <main className="mx-auto flex max-w-2xl flex-col gap-6 px-4 py-8">
      <h1 className="text-3xl">Account</h1>
      {children}
    </main>
  );
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  const id = `section-${title.toLowerCase().replace(/[^a-z]+/g, "-")}`;
  return (
    <section
      aria-labelledby={id}
      className="flex flex-col gap-4 rounded-md bg-surface p-6 shadow-card"
    >
      <h2 id={id} className="text-xl">
        {title}
      </h2>
      {children}
    </section>
  );
}

/** Runs an action, tracking busy state and a result message. */
function useAction() {
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ kind: "error" | "info"; text: ReactNode } | null>(null);
  const run = useCallback(async (action: () => Promise<ReactNode | undefined>) => {
    setBusy(true);
    setMessage(null);
    try {
      const done = await action();
      if (done) setMessage({ kind: "info", text: done });
    } catch (caught) {
      setMessage({ kind: "error", text: explain(caught) });
    } finally {
      setBusy(false);
    }
  }, []);
  const notice = message ? <Notice kind={message.kind}>{message.text}</Notice> : null;
  return { busy, run, notice };
}

function explain(error: unknown): ReactNode {
  if (error instanceof ApiError && error.code === "reauthentication_required") {
    return (
      <>
        For your security, sign in again first.{" "}
        <Link href="/login?next=/account" className="text-link underline">
          Sign in again
        </Link>
      </>
    );
  }
  return errorMessage(error);
}

function ProfileSection({ me }: { me: Me }) {
  const session = useSession();
  const [handle, setHandle] = useState(me.handle);
  const [displayName, setDisplayName] = useState(me.displayName);
  const { busy, run, notice } = useAction();

  function submit(event: FormEvent) {
    event.preventDefault();
    void run(async () => {
      const changes: { handle?: string; displayName?: string } = {};
      if (handle !== me.handle) changes.handle = handle;
      if (displayName !== me.displayName) changes.displayName = displayName;
      if (Object.keys(changes).length === 0) return "Nothing to save.";
      session.signedIn(await account.update(changes));
      return "Saved.";
    });
  }

  return (
    <Section title="Profile">
      <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
        {notice}
        <TextField
          id="display-name"
          label="Display name"
          autoComplete="nickname"
          maxLength={60}
          value={displayName}
          onChange={(event) => setDisplayName(event.target.value)}
        />
        <TextField
          id="handle"
          label="Handle"
          autoComplete="username"
          maxLength={30}
          value={handle}
          onChange={(event) => setHandle(event.target.value)}
          hint="3 to 30 letters, digits or underscores."
        />
        <Button busy={busy} className="self-start">
          Save profile
        </Button>
      </form>
    </Section>
  );
}

function EmailSection({ me }: { me: Me }) {
  const session = useSession();
  const [confirming, setConfirming] = useState(false);
  const { busy, run, notice } = useAction();

  if (!me.email) {
    return (
      <Section title="Email">
        {/* TODO(product): adding or changing an email (EMAIL_CHANGE codes) is not built yet. */}
        <p className="text-muted">
          This account has no email address, so it signs in with its linked provider.
        </p>
      </Section>
    );
  }
  const email = me.email;

  return (
    <Section title="Email">
      <p className="flex flex-wrap items-center gap-2">
        <span className="break-all">{email}</span>
        {me.emailVerified ? <Badge tone="success">Confirmed</Badge> : <Badge>Not confirmed</Badge>}
      </p>
      {me.emailVerified ? null : confirming ? (
        <CodeStep
          email={email}
          intro="We sent a confirmation code to"
          submitLabel="Confirm email"
          onSubmit={async (code) => {
            session.signedIn(await auth.verifyCode(email, code));
            setConfirming(false);
          }}
          onResend={() => account.resendConfirmation()}
        />
      ) : (
        <>
          {notice}
          <p className="text-muted">Confirm your email to comment and buy later on.</p>
          <Button
            type="button"
            variant="secondary"
            busy={busy}
            className="self-start"
            onClick={() =>
              void run(async () => {
                await account.resendConfirmation();
                setConfirming(true);
                return undefined;
              })
            }
          >
            Send confirmation code
          </Button>
        </>
      )}
    </Section>
  );
}

function PasswordSection({ me }: { me: Me }) {
  const session = useSession();
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const { busy, run, notice } = useAction();

  function submit(event: FormEvent) {
    event.preventDefault();
    void run(async () => {
      if (next.length < 10)
        throw new ApiError(400, "too_short", "Your new password needs at least 10 characters.");
      await account.changePassword(next, me.hasPassword ? current : undefined);
      setCurrent("");
      setNext("");
      await session.reload();
      return "Password saved. Every other device was signed out.";
    });
  }

  return (
    <Section title="Password">
      <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
        {notice}
        {me.hasPassword ? (
          <TextField
            id="current-password"
            label="Current password"
            type="password"
            autoComplete="current-password"
            value={current}
            onChange={(event) => setCurrent(event.target.value)}
          />
        ) : (
          <p className="text-muted">
            You sign in without a password. Add one to sign in with your email and password too.
          </p>
        )}
        <TextField
          id="new-password"
          label="New password"
          type="password"
          autoComplete="new-password"
          minLength={10}
          value={next}
          onChange={(event) => setNext(event.target.value)}
          hint="At least 10 characters. Every other device will be signed out."
        />
        <Button busy={busy} className="self-start">
          {me.hasPassword ? "Change password" : "Add password"}
        </Button>
      </form>
    </Section>
  );
}

const PROVIDER_NAMES: Record<Provider, string> = { google: "Google", apple: "Apple" };

function MethodsSection({ me, providers }: { me: Me; providers: ProviderConfig }) {
  const session = useSession();
  const { busy, run, notice } = useAction();
  const configured = (Object.keys(PROVIDER_NAMES) as Provider[]).filter(
    (provider) => providers[provider],
  );
  if (configured.length === 0 && me.providers.length === 0) {
    return null;
  }
  const linkable: ProviderConfig = {
    google: me.providers.includes("google") ? undefined : providers.google,
    apple: me.providers.includes("apple") ? undefined : providers.apple,
  };

  return (
    <Section title="Sign-in methods">
      {notice}
      <ul className="flex flex-col gap-2">
        {me.providers.map((provider) => (
          <li key={provider} className="flex items-center justify-between gap-4">
            <span>{PROVIDER_NAMES[provider as Provider] ?? provider} is linked</span>
            <Button
              type="button"
              variant="secondary"
              busy={busy}
              onClick={() =>
                void run(async () => {
                  await account.unlink(provider as Provider);
                  await session.reload();
                  return "Unlinked.";
                })
              }
            >
              Unlink
            </Button>
          </li>
        ))}
      </ul>
      <SocialButtons
        providers={linkable}
        onToken={async (provider, idToken, nonce) => {
          await account.link(provider, idToken, nonce);
          await session.reload();
        }}
      />
    </Section>
  );
}

function SessionsSection() {
  const session = useSession();
  const [sessions, setSessions] = useState<SessionView[] | null>(null);
  const { busy, run, notice } = useAction();

  const load = useCallback(async () => {
    setSessions(await account.sessions());
  }, []);

  useEffect(() => {
    void load().catch(() => setSessions([]));
  }, [load]);

  return (
    <Section title="Devices">
      {notice}
      {sessions === null ? (
        <Skeleton className="h-16 w-full" />
      ) : (
        <ul className="flex flex-col divide-y divide-line">
          {sessions.map((item) => (
            <li key={item.id} className="flex items-center justify-between gap-4 py-3">
              <div className="flex flex-col">
                <span className="flex items-center gap-2 font-semibold">
                  {describeDevice(item.userAgent)}
                  {item.current ? <Badge tone="success">This device</Badge> : null}
                </span>
                <span className="text-sm text-muted">
                  {item.ip ?? "Unknown address"} · active {relativeTime(item.lastUsedAt)}
                </span>
              </div>
              <Button
                type="button"
                variant="secondary"
                busy={busy}
                aria-label={`Sign out ${describeDevice(item.userAgent)}${item.current ? " (this device)" : ""}`}
                onClick={() =>
                  void run(async () => {
                    await account.revokeSession(item.id);
                    if (item.current) {
                      session.forget();
                      return undefined;
                    }
                    await load();
                    return "Signed out.";
                  })
                }
              >
                Sign out
              </Button>
            </li>
          ))}
        </ul>
      )}
      {sessions && sessions.length > 1 ? (
        <Button
          type="button"
          variant="secondary"
          busy={busy}
          className="self-start"
          onClick={() =>
            void run(async () => {
              await account.revokeOtherSessions();
              await load();
              return "Every other device was signed out.";
            })
          }
        >
          Sign out all other devices
        </Button>
      ) : null}
    </Section>
  );
}

function DeleteSection({ me, onDeleted }: { me: Me; onDeleted(eraseAfter: string): void }) {
  const session = useSession();
  const [open, setOpen] = useState(false);
  const [password, setPassword] = useState("");
  const { busy, run, notice } = useAction();

  function submit(event: FormEvent) {
    event.preventDefault();
    void run(async () => {
      const { eraseAfter } = await account.delete(me.hasPassword ? password : undefined);
      session.forget();
      onDeleted(eraseAfter);
      return undefined;
    });
  }

  return (
    <Section title="Delete account">
      <p className="text-muted">
        Your account is erased 30 days after you ask, with everything in it. Signing in before then
        cancels it.
      </p>
      {open ? (
        <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
          {notice}
          {me.hasPassword ? (
            <TextField
              id="delete-password"
              label="Your password, to confirm"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
          ) : null}
          <div className="flex flex-wrap gap-2">
            <Button variant="danger" busy={busy}>
              Delete my account
            </Button>
            <Button type="button" variant="secondary" onClick={() => setOpen(false)}>
              Keep my account
            </Button>
          </div>
        </form>
      ) : (
        <Button type="button" variant="danger" className="self-start" onClick={() => setOpen(true)}>
          Delete account…
        </Button>
      )}
    </Section>
  );
}
