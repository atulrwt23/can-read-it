# notifications

**Purpose.** Gets messages to readers. Email only for now: the account emails identity asks for (sign-in, sign-up, confirmation and reset codes, and the "you already have an account" notice). Follower emails on new chapters, push and in-app notifications come in phase 2. It reads nothing from other modules' tables.

## Public API
- `EmailSender`: sends one `EmailMessage(to, subject, text)`. The only way email leaves the app ([ADR 0007](../decisions/0007-email-over-smtp.md)).

## How account emails go out
1. Identity publishes `identity.AccountEmailRequested(to, kind, code, codeValidFor)` after its transaction commits.
2. `AccountEmails` handles it on another thread (`@Async @EventListener`), renders plain text and calls `EmailSender`.
3. A failed send is logged (kind and error, never the code) and dropped. The event is deliberately not a persisted Modulith event, so codes are never written to `modulith.event_publication`; codes expire in minutes, so retrying later would not help anyway.

Emails are plain text, with the brand from `app.brand.name`, and never put the code in the subject (it shows in lock-screen previews). `TODO(product)`: links back to the site once it has a stable address, and an HTML version.

## External port
| Interface | Adapter | Local | Beta |
|---|---|---|---|
| `EmailSender` | `SmtpEmailSender` (`JavaMailSender`, `spring.mail.*`) | Mailpit, http://localhost:8025 | Resend over SMTP, once a domain is verified ([deploy.md](../deploy.md#email-settings)) |

Without `spring.mail.host` (`SMTP_HOST`), `LoggingEmailSender` logs each subject and drops the email, so the app runs without email. The sender is `app.mail.from` (`MAIL_FROM`). Spring Boot's mail health check is off, so an email outage never marks the API down.

## Data
None yet.
