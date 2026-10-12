"use client";

import { createContext, type ReactNode, useCallback, useContext, useEffect, useState } from "react";
import { ApiError, auth, hasSignedInHint, type Me, setSignedInHint } from "@/lib/auth/client";

type SessionState =
  | { status: "loading" }
  | { status: "signed-out" }
  | { status: "signed-in"; me: Me };

type Session = SessionState & {
  /** After a sign-in: stores the user and remembers that this browser is signed in. */
  signedIn(me: Me): void;
  /** Ends the session on the server and forgets the user here. */
  signOut(): Promise<void>;
  /** Re-reads the user from the API (after a profile change elsewhere). */
  reload(): Promise<void>;
  /** After the session ended on the server (deleted account, signed out this device). */
  forget(): void;
};

const SessionContext = createContext<Session | null>(null);

/**
 * Who is signed in, for the account menu and account pages. Public pages render the same for everyone, so this is
 * only known after hydration; until then the state is "loading".
 */
export function SessionProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<SessionState>({ status: "loading" });

  const reload = useCallback(async () => {
    try {
      const me = await auth.me();
      setSignedInHint(true);
      setState({ status: "signed-in", me });
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        setSignedInHint(false);
      }
      setState({ status: "signed-out" });
    }
  }, []);

  useEffect(() => {
    if (hasSignedInHint()) {
      void reload();
    } else {
      setState({ status: "signed-out" });
    }
  }, [reload]);

  const signedIn = useCallback((me: Me) => {
    setSignedInHint(true);
    setState({ status: "signed-in", me });
  }, []);

  const forget = useCallback(() => {
    setSignedInHint(false);
    setState({ status: "signed-out" });
  }, []);

  const signOut = useCallback(async () => {
    try {
      await auth.logout();
    } finally {
      forget();
    }
  }, [forget]);

  return (
    <SessionContext value={{ ...state, signedIn, signOut, reload, forget }}>
      {children}
    </SessionContext>
  );
}

export function useSession(): Session {
  const session = useContext(SessionContext);
  if (!session) {
    throw new Error("useSession needs a SessionProvider");
  }
  return session;
}
