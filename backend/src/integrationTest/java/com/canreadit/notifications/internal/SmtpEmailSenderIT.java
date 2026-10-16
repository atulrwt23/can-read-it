package com.canreadit.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.canreadit.notifications.EmailMessage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/** The SMTP adapter against Mailpit, the same catcher local development uses. */
class SmtpEmailSenderIT {

    static final GenericContainer<?> mailpit = new GenericContainer<>("axllent/mailpit:v1.31.2")
            .withExposedPorts(1025, 8025)
            .waitingFor(Wait.forHttp("/readyz").forPort(8025));

    @BeforeAll
    static void start() {
        mailpit.start();
    }

    @AfterAll
    static void stop() {
        mailpit.stop();
    }

    @Test
    void sendsAPlainTextEmailOverSmtp() throws Exception {
        var mailSender = new JavaMailSenderImpl();
        mailSender.setHost(mailpit.getHost());
        mailSender.setPort(mailpit.getMappedPort(1025));
        var sender = new SmtpEmailSender(mailSender, new MailSettings("CanReadIt <no-reply@canreadit.test>"));

        sender.send(new EmailMessage("ana@example.com", "Your CanReadIt sign-in code", "Code: 123456 — ünïcödé"));

        String messages = HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create("http://" + mailpit.getHost() + ":"
                                        + mailpit.getMappedPort(8025) + "/api/v1/messages"))
                                .build(),
                        HttpResponse.BodyHandlers.ofString())
                .body();
        assertThat(messages)
                .contains("\"Subject\":\"Your CanReadIt sign-in code\"")
                .contains("ana@example.com")
                .contains("no-reply@canreadit.test")
                .contains("\"Name\":\"CanReadIt\"");
    }

    @Test
    void addsTheMessagesExtraHeaders() throws Exception {
        var mailSender = new JavaMailSenderImpl();
        mailSender.setHost(mailpit.getHost());
        mailSender.setPort(mailpit.getMappedPort(1025));
        var sender = new SmtpEmailSender(mailSender, new MailSettings("CanReadIt <no-reply@canreadit.test>"));

        sender.send(new EmailMessage(
                "ben@example.com",
                "Tower: chapter 2 is out",
                "Read it",
                Map.of(
                        "List-Unsubscribe", "<https://canreadit.test/api/v1/notifications/unsubscribe?token=t>",
                        "List-Unsubscribe-Post", "List-Unsubscribe=One-Click")));

        String headers = HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create("http://" + mailpit.getHost() + ":"
                                        + mailpit.getMappedPort(8025) + "/api/v1/message/latest/headers"))
                                .build(),
                        HttpResponse.BodyHandlers.ofString())
                .body();
        assertThat(headers)
                .contains(
                        "\"List-Unsubscribe\":[\"\\u003chttps://canreadit.test/api/v1/notifications/unsubscribe?token=t\\u003e\"]")
                .contains("\"List-Unsubscribe-Post\":[\"List-Unsubscribe=One-Click\"]");
    }
}
