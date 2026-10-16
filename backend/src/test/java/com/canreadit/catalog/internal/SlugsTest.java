package com.canreadit.catalog.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SlugsTest {

    @Test
    void lowercasesAndJoinsWordsWithHyphens() {
        assertThat(Slugs.fromTitle("The Duke's  Borrowed Name!")).isEqualTo("the-duke-s-borrowed-name");
    }

    @Test
    void dropsAccents() {
        assertThat(Slugs.fromTitle("Café Crème: Épisode 2")).isEqualTo("cafe-creme-episode-2");
    }

    @Test
    void isEmptyWithoutLatinLettersOrDigits() {
        assertThat(Slugs.fromTitle("별빛 탑")).isEmpty();
    }

    @Test
    void staysWithinTheLengthLimitWithoutATrailingHyphen() {
        String slug = Slugs.fromTitle("word ".repeat(40));
        assertThat(slug).hasSizeLessThanOrEqualTo(Slugs.MAX_LENGTH).doesNotEndWith("-");
        assertThat(slug).matches(CatalogController.SLUG);
    }
}
