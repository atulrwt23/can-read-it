package com.canreadit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenApiIT {

    @Autowired
    MockMvcTester mvc;

    @Test
    void recordsAreRequiredUnlessNullable() {
        assertThat(mvc.get().uri("/v3/api-docs"))
                .hasStatusOk()
                .bodyJson()
                .satisfies(json -> assertThat(json)
                        .extractingPath("$.components.schemas.SeriesCard.required")
                        .asArray()
                        .contains("id", "slug", "title", "genres", "latestChapters")
                        .doesNotContain("cover", "lastPublishedAt"))
                .satisfies(json -> assertThat(json)
                        .extractingPath("$.components.schemas.SeriesCard.properties.cover.anyOf[1].type")
                        .isEqualTo("null"))
                .satisfies(json -> assertThat(json)
                        .extractingPath("$.components.schemas.SeriesCard.properties.lastPublishedAt.type")
                        .asArray()
                        .containsExactly("string", "null"))
                .satisfies(json -> assertThat(json)
                        .extractingPath("$.components.schemas.CursorPageSeriesCard.required")
                        .asArray()
                        .containsExactly("items"));
    }
}
