package com.canreadit.notifications.internal;

import com.canreadit.notifications.EmailMessage;
import com.canreadit.notifications.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Used when no SMTP server is configured: drops emails with a warning that never includes the body. */
class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(EmailMessage message) {
        log.warn("No SMTP server configured (SMTP_HOST), so this email was not sent: \"{}\"", message.subject());
    }
}
