package com.canreadit.shared.internal;

import com.canreadit.shared.Problems;
import com.canreadit.shared.PublicGetEndpoints;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Deny by default. Modules open anonymous {@code GET} routes by registering {@link
 * PublicGetEndpoints} beans. Sign-in, JWT validation and CSRF double-submit cookies arrive with
 * the identity module (roadmap step 2).
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

    private static final String[] OPERATIONAL_GETS = {
        "/actuator/health", "/actuator/health/**", "/actuator/info", "/actuator/prometheus",
        "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**",
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, List<PublicGetEndpoints> publicEndpoints)
            throws Exception {
        String[] publicGets = publicEndpoints.stream()
                .flatMap(endpoints -> endpoints.patterns().stream())
                .toArray(String[]::new);
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    if (publicGets.length > 0) {
                        auth.requestMatchers(HttpMethod.GET, publicGets).permitAll();
                    }
                    auth.requestMatchers(HttpMethod.GET, OPERATIONAL_GETS).permitAll();
                    auth.requestMatchers("/error").permitAll();
                    auth.anyRequest().denyAll();
                })
                .exceptionHandling(errors -> errors.authenticationEntryPoint(
                                (request, response, ex) -> writeProblem(response, 401, "unauthorized"))
                        .accessDeniedHandler((request, response, ex) -> writeProblem(response, 403, "forbidden")));
        return http.build();
    }

    private static void writeProblem(HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        // Fixed strings only, so hand-written JSON is safe here.
        response.getWriter()
                .write("{\"type\":\"%s\",\"title\":\"%s\",\"status\":%d,\"code\":\"%s\"}"
                        .formatted(Problems.type(code), status == 401 ? "Unauthorized" : "Forbidden", status, code));
    }
}
