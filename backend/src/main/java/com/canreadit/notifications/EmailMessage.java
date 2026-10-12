package com.canreadit.notifications;

/** A plain-text email to one recipient. */
public record EmailMessage(String to, String subject, String text) {

    /** Keeps the body (which may hold a code) out of logs. */
    @Override
    public String toString() {
        return "EmailMessage[subject=" + subject + "]";
    }
}
