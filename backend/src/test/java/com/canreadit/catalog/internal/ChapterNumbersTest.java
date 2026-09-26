package com.canreadit.catalog.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.canreadit.shared.ApiException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ChapterNumbersTest {

    @ParameterizedTest
    @CsvSource({"12.00, 12", "12.50, 12.5", "0.00, 0", "10, 10", "100.05, 100.05"})
    void formatsWithoutTrailingZeros(String stored, String shown) {
        assertThat(ChapterNumbers.format(new BigDecimal(stored))).isEqualTo(shown);
    }

    @Test
    void parsesDecimals() {
        assertThat(ChapterNumbers.parse("12.5")).isEqualByComparingTo("12.5");
        assertThat(ChapterNumbers.parse("12.50")).isEqualByComparingTo("12.5");
        assertThat(ChapterNumbers.parse("999999.99")).isEqualByComparingTo("999999.99");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "-1", "1.234", "1000000", "1e3", "NaN", "0x10"})
    void rejectsInvalidNumbers(String raw) {
        assertThatThrownBy(() -> ChapterNumbers.parse(raw))
                .isInstanceOfSatisfying(
                        ApiException.class, e -> assertThat(e.code()).isEqualTo("invalid_chapter_number"));
    }
}
