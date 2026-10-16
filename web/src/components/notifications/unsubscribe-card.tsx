"use client";

import Link from "next/link";
import { useState } from "react";
import { Button, Card, Notice } from "@/components/auth/form";

const TOKEN = /^[A-Za-z0-9_-]{32}$/;

type State = "ready" | "busy" | "done" | "invalid" | "error";

/**
 * Turns new-chapter emails off with the token from an email. It waits for a click, because mail scanners open links
 * on their own. Needs no account: the token is the credential, and no cookie is sent.
 */
export function UnsubscribeCard({ token }: { token: string | null }) {
  const [state, setState] = useState<State>(token && TOKEN.test(token) ? "ready" : "invalid");

  async function unsubscribe() {
    if (!token) return;
    setState("busy");
    try {
      const response = await fetch(
        `/api/v1/notifications/unsubscribe?token=${encodeURIComponent(token)}`,
        { method: "POST", credentials: "omit" },
      );
      setState(response.ok ? "done" : response.status === 404 ? "invalid" : "error");
    } catch {
      setState("error");
    }
  }

  if (state === "done") {
    return (
      <Card title="You're unsubscribed">
        <Notice kind="info">We won't email you about new chapters any more.</Notice>
        <p>
          Changed your mind? Turn them back on in{" "}
          <Link href="/account" className="text-link underline">
            your account
          </Link>
          .
        </p>
      </Card>
    );
  }
  if (state === "invalid") {
    return (
      <Card title="This link doesn't work">
        <p>
          The unsubscribe link is incomplete or out of date. You can turn new-chapter emails off in{" "}
          <Link href="/account" className="text-link underline">
            your account
          </Link>{" "}
          instead.
        </p>
      </Card>
    );
  }
  return (
    <Card title="Stop new-chapter emails">
      {state === "error" ? (
        <Notice kind="error">Something went wrong. Check your connection and try again.</Notice>
      ) : null}
      <p>
        You get an email when series you follow have new chapters. Stop these emails? Your library
        stays as it is.
      </p>
      <Button
        type="button"
        busy={state === "busy"}
        className="self-start"
        onClick={() => void unsubscribe()}
      >
        Stop new-chapter emails
      </Button>
    </Card>
  );
}
