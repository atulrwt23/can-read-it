package com.canreadit;

import com.canreadit.notifications.EmailMessage;
import com.canreadit.notifications.EmailSender;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Catches the emails the app sends, in place of SMTP. Account emails go out asynchronously, so tests wait. */
public class RecordingEmailSender implements EmailSender {

    private static final Pattern CODE = Pattern.compile("\\b(\\d{6})\\b");

    private final List<EmailMessage> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(EmailMessage message) {
        sent.add(message);
    }

    public void clear() {
        sent.clear();
    }

    public List<EmailMessage> sentTo(String to) {
        return sent.stream().filter(m -> m.to().equalsIgnoreCase(to)).toList();
    }

    /** Waits for the {@code n}th email (1-based) to this address. */
    public EmailMessage await(String to, int n) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (Instant.now().isBefore(deadline)) {
            var messages = sentTo(to);
            if (messages.size() >= n) {
                return messages.get(n - 1);
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new AssertionError("No email number " + n + " to " + to);
    }

    public EmailMessage await(String to) {
        return await(to, 1);
    }

    /** The 6-digit code in an email. */
    public static String codeIn(EmailMessage message) {
        var matcher = CODE.matcher(message.text());
        if (!matcher.find()) {
            throw new AssertionError("No code in \"" + message.subject() + "\"");
        }
        return matcher.group(1);
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        @Primary
        RecordingEmailSender recordingEmailSender() {
            return new RecordingEmailSender();
        }
    }
}
