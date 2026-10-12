package com.canreadit.notifications;

/**
 * Sends one email. The only way out of the app for email (ADR 0007): the adapter speaks SMTP, so switching provider
 * is configuration.
 */
public interface EmailSender {

    /** Sends the message or throws; callers decide whether a failure matters. */
    void send(EmailMessage message);
}
