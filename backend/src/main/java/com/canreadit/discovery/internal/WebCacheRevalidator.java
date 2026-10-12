package com.canreadit.discovery.internal;

import com.canreadit.catalog.ChapterPublished;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Refreshes the web app's cached pages when a chapter goes live: the home page, series lists and that series. The
 * web app exposes a secret-protected endpoint for this (web/src/app/internal/revalidate). Without one configured,
 * cached pages still refresh on their own timers, about a minute later.
 */
@Component
class WebCacheRevalidator {

    private static final Logger log = LoggerFactory.getLogger(WebCacheRevalidator.class);

    private final WebRevalidation config;
    private final RestClient http;

    WebCacheRevalidator(WebRevalidation config) {
        this.config = config;
        var client =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        var requests = new JdkClientHttpRequestFactory(client);
        requests.setReadTimeout(Duration.ofSeconds(5));
        this.http = RestClient.builder().requestFactory(requests).build();
    }

    @ApplicationModuleListener
    void on(ChapterPublished event) {
        revalidate(List.of("home", "series-lists", "series:" + event.seriesSlug()));
    }

    void revalidate(List<String> tags) {
        if (!config.enabled()) {
            return;
        }
        try {
            http.post()
                    .uri(config.revalidateUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.revalidateSecret())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("tags", tags))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            // Not worth retrying: the pages refresh on their timers anyway.
            log.warn("Could not refresh the web cache for {}: {}", tags, e.getMessage());
        }
    }

    /**
     * @param revalidateUrl the web app's revalidation endpoint, e.g. {@code http://web:3000/internal/revalidate}
     * @param revalidateSecret shared with the web app (REVALIDATE_SECRET)
     */
    @ConfigurationProperties("app.web")
    record WebRevalidation(
            @Nullable String revalidateUrl, @Nullable String revalidateSecret) {

        boolean enabled() {
            return revalidateUrl != null
                    && !revalidateUrl.isBlank()
                    && revalidateSecret != null
                    && !revalidateSecret.isBlank();
        }
    }
}
