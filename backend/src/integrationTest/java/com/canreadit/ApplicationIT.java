package com.canreadit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ApplicationIT {

    @Autowired
    JdbcClient jdbc;

    @Autowired
    MockMvcTester mvc;

    @Test
    void migrationsCreateEveryModuleSchema() {
        var schemas = jdbc.sql("select schema_name from information_schema.schemata")
                .query(String.class)
                .list();

        assertThat(schemas).contains("identity", "catalog", "media", "discovery", "modulith");
    }

    @Test
    void healthIsPublic() {
        assertThat(mvc.get().uri("/actuator/health")).hasStatusOk();
    }

    @Test
    void unknownRoutesAreDeniedWithProblemDetails() {
        assertThat(mvc.get().uri("/api/v1/nope"))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .hasHeader("Content-Type", "application/problem+json;charset=UTF-8")
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("unauthorized");
    }

    @Test
    void everyResponseCarriesARequestId() {
        assertThat(mvc.get().uri("/actuator/health").header("X-Request-Id", "edge-123"))
                .hasHeader("X-Request-Id", "edge-123");
        var generated = mvc.get()
                .uri("/actuator/health")
                .header("X-Request-Id", "bad id <script>")
                .exchange()
                .getResponse()
                .getHeader("X-Request-Id");
        assertThat(generated).isNotEqualTo("bad id <script>").hasSize(36);
    }
}
