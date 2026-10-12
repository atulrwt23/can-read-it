"use client";

import Script from "next/script";
import { useEffect, useRef, useState } from "react";
import { auth, errorMessage, type Provider } from "@/lib/auth/client";
import { Notice } from "./form";

/** Web client IDs of the configured providers; a provider without one shows no button. */
export type ProviderConfig = { google?: string; apple?: string };

type GoogleId = {
  initialize(options: {
    client_id: string;
    nonce: string;
    callback(response: { credential: string }): void;
  }): void;
  renderButton(
    element: HTMLElement,
    options: { theme: string; size: string; text: string; width: number },
  ): void;
};
type AppleAuth = {
  init(options: {
    clientId: string;
    scope: string;
    redirectURI: string;
    usePopup: boolean;
    nonce: string;
  }): void;
  signIn(): Promise<{ authorization: { id_token: string } }>;
};
declare global {
  interface Window {
    google?: { accounts: { id: GoogleId } };
    AppleID?: { auth: AppleAuth };
  }
}

/**
 * "Continue with Google / Apple". Each button gets a one-time nonce from the API, hands it to the provider's
 * script, and passes the resulting ID token and nonce to `onToken` (sign in, or link to the signed-in account).
 * The providers' scripts load only when they are configured.
 */
export function SocialButtons({
  providers,
  onToken,
}: {
  providers: ProviderConfig;
  onToken(provider: Provider, idToken: string, nonce: string): Promise<void>;
}) {
  const [error, setError] = useState<string | null>(null);
  if (!providers.google && !providers.apple) {
    return null;
  }

  async function handle(provider: Provider, idToken: string, nonce: string) {
    setError(null);
    try {
      await onToken(provider, idToken, nonce);
    } catch (caught) {
      setError(errorMessage(caught));
    }
  }

  return (
    <div className="flex flex-col items-stretch gap-3">
      {error ? <Notice kind="error">{error}</Notice> : null}
      {providers.google ? (
        <GoogleButton
          clientId={providers.google}
          onToken={(token, nonce) => handle("google", token, nonce)}
        />
      ) : null}
      {providers.apple ? (
        <AppleButton
          clientId={providers.apple}
          onToken={(token, nonce) => handle("apple", token, nonce)}
          onError={setError}
        />
      ) : null}
    </div>
  );
}

function GoogleButton({
  clientId,
  onToken,
}: {
  clientId: string;
  onToken(idToken: string, nonce: string): Promise<void>;
}) {
  const container = useRef<HTMLDivElement>(null);
  const callback = useRef(onToken);
  callback.current = onToken;
  const [loaded, setLoaded] = useState(false);
  // Each attempt spends its nonce, so every round gets a fresh one.
  const [round, setRound] = useState(0);

  // biome-ignore lint/correctness/useExhaustiveDependencies: `round` re-runs this with a new nonce after each attempt
  useEffect(() => {
    const google = window.google;
    if (!loaded || !google || !container.current) return;
    let cancelled = false;
    void auth.nonce().then(({ nonce }) => {
      if (cancelled || !container.current) return;
      google.accounts.id.initialize({
        client_id: clientId,
        nonce,
        callback: ({ credential }) => {
          void callback.current(credential, nonce).finally(() => setRound((value) => value + 1));
        },
      });
      google.accounts.id.renderButton(container.current, {
        theme: "outline",
        size: "large",
        text: "continue_with",
        width: 320,
      });
    });
    return () => {
      cancelled = true;
    };
  }, [loaded, clientId, round]);

  return (
    <>
      <Script src="https://accounts.google.com/gsi/client" onReady={() => setLoaded(true)} />
      <div ref={container} className="flex min-h-10 justify-center" />
    </>
  );
}

function AppleButton({
  clientId,
  onToken,
  onError,
}: {
  clientId: string;
  onToken(idToken: string, nonce: string): Promise<void>;
  onError(message: string): void;
}) {
  const [loaded, setLoaded] = useState(false);

  async function signIn() {
    const apple = window.AppleID;
    if (!apple) return;
    try {
      const { nonce } = await auth.nonce();
      apple.auth.init({
        clientId,
        scope: "email",
        redirectURI: `${window.location.origin}/login`,
        usePopup: true,
        nonce,
      });
      const response = await apple.auth.signIn();
      await onToken(response.authorization.id_token, nonce);
    } catch (caught) {
      // Closing Apple's popup lands here too; only API errors are worth showing.
      if (caught instanceof Error) onError(errorMessage(caught));
    }
  }

  return (
    <>
      <Script
        src="https://appleid.cdn-apple.com/appleauth/static/jsapi/appleid/1/en_US/appleid.auth.js"
        onReady={() => setLoaded(true)}
      />
      <button
        type="button"
        onClick={signIn}
        disabled={!loaded}
        className="mx-auto inline-flex h-10 w-80 max-w-full items-center justify-center gap-2 rounded bg-black px-4 text-sm font-semibold text-white disabled:opacity-60 dark:bg-white dark:text-black"
      >
        <svg aria-hidden="true" viewBox="0 0 24 24" className="size-4" fill="currentColor">
          <path d="M16.4 12.6c0-2.5 2-3.7 2.1-3.8-1.2-1.7-3-1.9-3.6-2-1.5-.2-3 .9-3.8.9-.8 0-2-.9-3.3-.9-1.7 0-3.3 1-4.2 2.5-1.8 3.1-.5 7.7 1.3 10.2.9 1.2 1.9 2.6 3.2 2.6 1.3-.1 1.8-.8 3.3-.8 1.6 0 2 .8 3.3.8 1.4 0 2.3-1.3 3.1-2.5 1-1.4 1.4-2.8 1.4-2.9 0 0-2.8-1.1-2.8-4.1ZM14 5.2c.7-.9 1.2-2 1-3.2-1 0-2.3.7-3 1.6-.7.8-1.3 2-1.1 3.1 1.1.1 2.3-.6 3.1-1.5Z" />
        </svg>
        Continue with Apple
      </button>
    </>
  );
}
