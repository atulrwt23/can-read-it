package com.canreadit.notifications.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Email settings ({@code app.mail}). The SMTP server itself is {@code spring.mail.*}.
 *
 * @param from the sender, for example {@code CanReadIt <no-reply@example.com>}; its domain must be verified with the
 *     provider (SPF, DKIM)
 */
@ConfigurationProperties("app.mail")
record MailSettings(String from) {}
