package com.canreadit.shared.internal;

import com.canreadit.shared.AuthCookies;
import com.canreadit.shared.AuthenticatedEndpoints;
import com.canreadit.shared.CookieSettings;
import com.canreadit.shared.Problems;
import com.canreadit.shared.PublicGetEndpoints;
import com.canreadit.shared.PublicPostEndpoints;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Deny by default. Modules open routes by registering {@link PublicGetEndpoints}, {@link PublicPostEndpoints} and
 * {@link AuthenticatedEndpoints} beans.
 *
 * <p>Signed-in requests carry our own ES256 JWT (identity module), from the {@code Authorization: Bearer} header
 * (mobile) or the {@code access_token} cookie (web). Public routes ignore the cookie, so a stale one never breaks a
 * public page. Requests that rely on cookies must carry the CSRF token: the {@code XSRF-TOKEN} cookie echoed in the
 * {@code X-XSRF-TOKEN} header (double submit). Requests with an {@code Authorization} header carry no ambient
 * credentials and skip that check.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

    private static final String[] OPERATIONAL_GETS = {
        "/actuator/health", "/actuator/health/**", "/actuator/info", "/actuator/prometheus",
        "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**",
    };

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            List<PublicGetEndpoints> publicGetEndpoints,
            List<PublicPostEndpoints> publicPostEndpoints,
            List<AuthenticatedEndpoints> authenticatedEndpoints,
            CookieSettings cookies)
            throws Exception {
        String[] publicGets = Stream.concat(
                        publicGetEndpoints.stream().flatMap(e -> e.patterns().stream()), Stream.of(OPERATIONAL_GETS))
                .toArray(String[]::new);
        String[] csrfFreePosts = publicPostEndpoints.stream()
                .filter(e -> !e.csrfProtected())
                .flatMap(e -> e.patterns().stream())
                .toArray(String[]::new);
        String[] publicPosts =
                publicPostEndpoints.stream().flatMap(e -> e.patterns().stream()).toArray(String[]::new);
        String[] signedIn = authenticatedEndpoints.stream()
                .flatMap(e -> e.patterns().stream())
                .toArray(String[]::new);

        RequestMatcher publicRoute = publicRoutes(publicGets, publicPosts);
        var csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieCustomizer(
                cookie -> cookie.path("/").sameSite("Lax").secure(cookies.secure()));

        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> {
                    csrf.spa().csrfTokenRepository(csrfRepository);
                    csrf.ignoringRequestMatchers(request -> request.getHeader(HttpHeaders.AUTHORIZATION) != null);
                    if (csrfFreePosts.length > 0) {
                        csrf.ignoringRequestMatchers(csrfFreePosts);
                    }
                })
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(HttpMethod.GET, publicGets).permitAll();
                    if (publicPosts.length > 0) {
                        auth.requestMatchers(HttpMethod.POST, publicPosts).permitAll();
                    }
                    if (signedIn.length > 0) {
                        auth.requestMatchers(signedIn).authenticated();
                    }
                    auth.requestMatchers("/error").permitAll();
                    auth.anyRequest().denyAll();
                })
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .bearerTokenResolver(new HeaderOrCookieTokenResolver(publicRoute))
                        .authenticationEntryPoint(
                                (request, response, ex) -> writeProblem(response, 401, "unauthorized"))
                        .jwt(Customizer.withDefaults()))
                .exceptionHandling(errors -> errors.authenticationEntryPoint(
                                (request, response, ex) -> writeProblem(response, 401, "unauthorized"))
                        .accessDeniedHandler((request, response, ex) -> writeProblem(response, 403, "forbidden")));
        return http.build();
    }

    private static RequestMatcher publicRoutes(String[] publicGets, String[] publicPosts) {
        var matchers = new ArrayList<RequestMatcher>();
        var builder = PathPatternRequestMatcher.withDefaults();
        for (String pattern : publicGets) {
            matchers.add(builder.matcher(HttpMethod.GET, pattern));
        }
        for (String pattern : publicPosts) {
            matchers.add(builder.matcher(HttpMethod.POST, pattern));
        }
        return new OrRequestMatcher(matchers);
    }

    /** The bearer header when present; otherwise the access cookie, except on public routes. */
    static final class HeaderOrCookieTokenResolver implements BearerTokenResolver {

        private static final String PREFIX = "Bearer ";

        private final RequestMatcher publicRoute;

        HeaderOrCookieTokenResolver(RequestMatcher publicRoute) {
            this.publicRoute = publicRoute;
        }

        @Override
        public @Nullable String resolve(HttpServletRequest request) {
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (header != null && header.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
                String token = header.substring(PREFIX.length()).strip();
                return token.isEmpty() ? null : token;
            }
            if (publicRoute.matches(request) || request.getCookies() == null) {
                return null;
            }
            for (Cookie cookie : request.getCookies()) {
                if (AuthCookies.ACCESS.equals(cookie.getName())
                        && !cookie.getValue().isBlank()) {
                    return cookie.getValue();
                }
            }
            return null;
        }
    }

    private static void writeProblem(HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        // Fixed strings only, so hand-written JSON is safe here.
        response.getWriter()
                .write("{\"type\":\"%s\",\"title\":\"%s\",\"status\":%d,\"code\":\"%s\"}"
                        .formatted(Problems.type(code), status == 401 ? "Unauthorized" : "Forbidden", status, code));
    }
}
