package com.canreadit.identity.internal;

import java.net.http.HttpClient;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Cloudflare Turnstile, checked server-side with the siteverify endpoint. Off when no secret key is configured
 * (local development, or before a widget exists). When Cloudflare can't be reached, the check fails: a form that
 * creates accounts should not open up because the bot check is down.
 */
@Component
class TurnstileVerifier implements BotChallengeVerifier {

    private static final Logger log = LoggerFactory.getLogger(TurnstileVerifier.class);

    private final AuthProperties.Turnstile settings;
    private final RestClient http;

    TurnstileVerifier(AuthProperties properties) {
        this.settings = properties.turnstile();
        var client =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        var requests = new JdkClientHttpRequestFactory(client);
        requests.setReadTimeout(Duration.ofSeconds(5));
        this.http = RestClient.builder().requestFactory(requests).build();
        if (!enabled()) {
            log.info("Bot challenge off: no app.auth.turnstile.secret-key (TURNSTILE_SECRET_KEY)");
        }
    }

    boolean enabled() {
        return settings.secretKey() != null && !settings.secretKey().isBlank();
    }

    @Override
    public boolean verify(@Nullable String token, String clientIp) {
        if (!enabled()) {
            return true;
        }
        if (token == null || token.isBlank() || token.length() > 2048) {
            return false;
        }
        var form = new LinkedMultiValueMap<String, String>();
        form.add("secret", settings.secretKey());
        form.add("response", token);
        form.add("remoteip", clientIp);
        try {
            var result = http.post()
                    .uri(settings.verifyUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(SiteverifyResult.class);
            return result != null && result.success();
        } catch (RestClientException e) {
            log.warn("Turnstile check failed, rejecting: {}", e.getMessage());
            return false;
        }
    }

    record SiteverifyResult(boolean success) {}
}
