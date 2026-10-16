package com.canreadit.shared.internal;

import com.canreadit.shared.AuthCookies;
import com.canreadit.shared.AuthenticatedEndpoints;
import com.canreadit.shared.CookieSettings;
import com.canreadit.shared.Problems;
import com.canreadit.shared.PublicGetEndpoints;
import com.canreadit.shared.PublicPostEndpoints;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Deny by default. Endpoints that need a permission also say so with {@code @PreAuthorize} (method security is on).
 * Modules open routes by registering {@link PublicGetEndpoints}, {@link PublicPostEndpoints} and
 * {@link AuthenticatedEndpoints} beans.
 *
 * <p>Signed-in requests carry our own ES256 JWT (identity module), from the {@code Authorization: Bearer} header
 * (mobile) or the {@code access_token} cookie (web). Public routes ignore the cookie, so a stale one never breaks a
 * public page. Requests that rely on cookies must carry the CSRF token: the {@code XSRF-TOKEN} cookie echoed in the
 * {@code X-XSRF-TOKEN} header (double submit). Requests with an {@code Authorization} header carry no ambient
 * credentials and skip that check.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
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
                    csrf.csrfTokenRepository(csrfRepository)
                            .csrfTokenRequestHandler(new SpaCsrfHandler(publicRoutes(publicGets, new String[0])));
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
                // Marks requests that passed the CSRF check; only those may use the access cookie.
                .addFilterAfter(new CsrfCheckedFilter(), CsrfFilter.class)
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

    /**
     * Single-page-app CSRF handling, like {@code csrf.spa()}, except that public reads never set the cookie. The
     * stock handler writes a new {@code XSRF-TOKEN} cookie on every response that lacks one, so cacheable public
     * responses would carry {@code Set-Cookie}, and a shared cache could hand one token to everyone. The browser gets
     * its token from any other response instead: {@code GET /api/v1/me} or a sign-in.
     */
    static final class SpaCsrfHandler implements CsrfTokenRequestHandler {

        private final CsrfTokenRequestAttributeHandler plain = new CsrfTokenRequestAttributeHandler();
        private final XorCsrfTokenRequestAttributeHandler xor = new XorCsrfTokenRequestAttributeHandler();
        private final RequestMatcher publicRead;

        SpaCsrfHandler(RequestMatcher publicRead) {
            this.publicRead = publicRead;
        }

        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> token) {
            // BREACH protection for a token rendered into a page; our client reads the cookie instead.
            xor.handle(request, response, token);
            if (!publicRead.matches(request)) {
                // Load the deferred token, which writes the cookie if the request had none.
                token.get();
            }
        }

        @Override
        public @Nullable String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken token) {
            // A header holds the raw cookie value (double submit); a form parameter holds the masked one.
            String header = request.getHeader(token.getHeaderName());
            return header != null && !header.isBlank()
                    ? plain.resolveCsrfTokenValue(request, token)
                    : xor.resolveCsrfTokenValue(request, token);
        }
    }

    /**
     * Runs right after {@link CsrfFilter}. The resource server tells CSRF to skip any request its token resolver finds
     * a token in, which is right for the bearer header (no ambient credentials) but wrong for the cookie. So the
     * resolver reads the cookie only once this marker is set, which is too late for the CSRF filter to see it.
     */
    static final class CsrfCheckedFilter extends OncePerRequestFilter {

        static final String ATTRIBUTE = CsrfCheckedFilter.class.getName();

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            request.setAttribute(ATTRIBUTE, Boolean.TRUE);
            chain.doFilter(request, response);
        }
    }

    /**
     * The bearer header when present; otherwise the access cookie, except on public routes and before the CSRF check
     * (see {@link CsrfCheckedFilter}).
     */
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
            if (request.getAttribute(CsrfCheckedFilter.ATTRIBUTE) == null
                    || publicRoute.matches(request)
                    || request.getCookies() == null) {
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
