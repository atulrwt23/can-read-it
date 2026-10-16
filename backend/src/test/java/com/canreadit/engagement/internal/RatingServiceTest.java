package com.canreadit.engagement.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RatingServiceTest {

    @Test
    void averagesToTwoDecimals() {
        var summary = RatingService.summary(new RatingRepository.Totals(3, 11));
        assertThat(summary.average()).isEqualByComparingTo(new BigDecimal("3.67"));
        assertThat(summary.count()).isEqualTo(3);
    }

    @Test
    void hasNoAverageBeforeTheFirstRating() {
        var summary = RatingService.summary(RatingRepository.Totals.NONE);
        assertThat(summary.average()).isNull();
        assertThat(summary.count()).isZero();
    }
}
