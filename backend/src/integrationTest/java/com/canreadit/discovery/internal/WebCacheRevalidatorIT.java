package com.canreadit.discovery.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.canreadit.TestcontainersConfiguration;
import com.canreadit.catalog.ChapterPublished;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/** A published chapter makes the API call the web app's revalidation endpoint, played here by a stub. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class WebCacheRevalidatorIT {

    static final BlockingQueue<String> REQUESTS = new LinkedBlockingQueue<>();
    static final HttpServer WEB = startWeb();

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    TransactionTemplate tx;

    @DynamicPropertySource
    static void web(DynamicPropertyRegistry registry) {
        registry.add(
                "app.web.revalidate-url",
                () -> "http://localhost:" + WEB.getAddress().getPort() + "/internal/revalidate");
        registry.add("app.web.revalidate-secret", () -> "test-secret");
    }

    @BeforeEach
    void setUp() {
        REQUESTS.clear();
    }

    @AfterAll
    static void stopWeb() {
        WEB.stop(0);
    }

    @Test
    void refreshesHomeListsAndTheSeriesWhenAChapterGoesLive() throws InterruptedException {
        var published =
                new ChapterPublished(UUID.randomUUID(), "night-library", UUID.randomUUID(), "1", "en", Instant.now());

        // Module listeners run after the publishing transaction commits.
        tx.executeWithoutResult(status -> events.publishEvent(published));

        String request = REQUESTS.poll(10, TimeUnit.SECONDS);
        assertThat(request)
                .isEqualTo("Bearer test-secret {\"tags\":[\"home\",\"series-lists\",\"series:night-library\"]}");
    }

    private static HttpServer startWeb() {
        try {
            var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/internal/revalidate", exchange -> {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                REQUESTS.add(exchange.getRequestHeaders().getFirst("Authorization") + " " + body);
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
