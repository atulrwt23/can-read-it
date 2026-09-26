# 0007. Email over SMTP, Resend for the beta

**Status:** Accepted (2026-09-26)

## Context
Sign-in codes and password resets need reliable transactional email. Mailpit covers local development only. The beta must be free, and the provider must be easy to replace.

## Decision
- All email goes through `notifications.EmailSender`, implemented with Spring Mail (`JavaMailSender`) over **SMTP**.
- Beta provider: **Resend** over SMTP (free tier), with the sending domain verified via SPF and DKIM. Local: Mailpit.
- Templates are rendered in the app (plain text + HTML). No provider-side templates.

## Consequences
- Switching to SES, Postmark, Brevo or any other relay means changing `spring.mail.*` settings only.
- No provider-specific features (webhooks for bounces, provider analytics) for now. Bounce handling will get its own ADR when needed.
