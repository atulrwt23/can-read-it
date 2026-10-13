package com.canreadit.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.canreadit.RecordingEmailSender;
import com.canreadit.TestcontainersConfiguration;
import com.canreadit.shared.AuthCookies;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

/** A clean identity schema, Redis and inbox for each test, and the HTTP calls the sign-in tests share. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RecordingEmailSender.Config.class})
abstract class IdentityTestSupport {

    static final String PASSWORD = "correct horse battery";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    StringRedisTemplate redis;

    @Autowired
    JsonMapper json;

    @Autowired
    RecordingEmailSender mail;

    @BeforeEach
    void cleanState() {
        jdbc.sql("truncate identity.users, identity.credentials, identity.user_identities, identity.user_roles,"
                        + " identity.sessions, identity.refresh_tokens, identity.verification_codes,"
                        + " identity.auth_events cascade")
                .update();
        redis.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
        mail.clear();
    }

    MvcTestResult register(String email, String handle) {
        return post("/api/v1/auth/password/register", Map.of("email", email, "password", PASSWORD, "handle", handle));
    }

    MvcTestResult login(String email, String password) {
        return post("/api/v1/auth/password/login", Map.of("email", email, "password", password));
    }

    /** Registers with a password and signs in with it, leaving the email unconfirmed. Returns the access cookie. */
    Cookie signUp(String email, String handle) {
        assertThat(register(email, handle)).hasStatus(202);
        var signedIn = login(email, PASSWORD);
        assertThat(signedIn).hasStatusOk();
        return signedIn.getResponse().getCookie(AuthCookies.ACCESS);
    }

    MvcTestResult refresh(Cookie refreshCookie) {
        return mvc.post().uri("/api/v1/auth/refresh").cookie(refreshCookie).exchange();
    }

    MvcTestResult me(Cookie access) {
        return mvc.get().uri("/api/v1/me").cookie(access).exchange();
    }

    /** The XSRF-TOKEN cookie, which every response except public reads hands out (here a signed-out /me). */
    Cookie csrfCookie() {
        return mvc.get().uri("/api/v1/me").exchange().getResponse().getCookie("XSRF-TOKEN");
    }

    /** A signed-in write: the access cookie plus the CSRF cookie and header. */
    MvcTestResult write(HttpMethod method, String uri, Cookie access, @Nullable Object body) {
        Cookie csrf = csrfCookie();
        var request = mvc.method(method).uri(uri).cookie(access, csrf).header("X-XSRF-TOKEN", csrf.getValue());
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return request.exchange();
    }

    MvcTestResult post(String uri, Map<String, String> body) {
        return mvc.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))
                .exchange();
    }

    long count(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }
}
