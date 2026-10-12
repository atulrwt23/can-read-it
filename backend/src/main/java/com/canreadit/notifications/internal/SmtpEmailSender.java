package com.canreadit.notifications.internal;

import com.canreadit.notifications.EmailMessage;
import com.canreadit.notifications.EmailSender;
import jakarta.mail.MessagingException;
import java.nio.charset.StandardCharsets;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/** Sends over SMTP ({@code spring.mail.*}): Mailpit locally, Resend in the beta, any provider later. */
class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final MailSettings settings;

    SmtpEmailSender(JavaMailSender mailSender, MailSettings settings) {
        this.mailSender = mailSender;
        this.settings = settings;
    }

    @Override
    public void send(EmailMessage message) {
        var mime = mailSender.createMimeMessage();
        try {
            var helper = new MimeMessageHelper(mime, false, StandardCharsets.UTF_8.name());
            helper.setFrom(settings.from());
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            helper.setText(message.text(), false);
        } catch (MessagingException e) {
            throw new MailPreparationException("Could not build the email", e);
        }
        mailSender.send(mime);
    }
}
