package com.canreadit.notifications.internal;

import com.canreadit.notifications.EmailSender;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration(proxyBeanMethods = false)
class MailConfig {

    /** SMTP when {@code spring.mail.host} is set; otherwise emails are logged and dropped, so the app still runs. */
    @Bean
    EmailSender emailSender(
            ObjectProvider<JavaMailSender> mailSender,
            MailSettings settings,
            @Value("${spring.mail.host:}") String host) {
        JavaMailSender sender = mailSender.getIfAvailable();
        return host.isBlank() || sender == null ? new LoggingEmailSender() : new SmtpEmailSender(sender, settings);
    }
}
