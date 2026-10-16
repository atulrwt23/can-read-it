package com.canreadit.notifications;

import java.util.Map;

/**
 * A plain-text email to one recipient.
 *
 * @param headers extra headers, such as {@code List-Unsubscribe}
 */
public record EmailMessage(String to, String subject, String text, Map<String, String> headers) {

    public EmailMessage {
        headers = Map.copyOf(headers);
    }

    public EmailMessage(String to, String subject, String text) {
        this(to, subject, text, Map.of());
    }

    /** Keeps the body (which may hold a code) out of logs. */
    @Override
    public String toString() {
        return "EmailMessage[subject=" + subject + "]";
    }
}
