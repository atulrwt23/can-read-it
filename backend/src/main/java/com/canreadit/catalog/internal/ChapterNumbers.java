package com.canreadit.catalog.internal;

import com.canreadit.shared.ApiException;
import java.math.BigDecimal;

/** Chapter numbers are numeric(8,2): up to 999999.99, shown without trailing zeros. */
final class ChapterNumbers {

    private static final BigDecimal MAX = new BigDecimal("999999.99");

    private ChapterNumbers() {}

    static String format(BigDecimal number) {
        return number.stripTrailingZeros().toPlainString();
    }

    static BigDecimal parse(String raw) {
        BigDecimal number;
        try {
            number = new BigDecimal(raw);
        } catch (NumberFormatException e) {
            throw invalid(raw);
        }
        if (raw.length() > 12
                || number.signum() < 0
                || number.compareTo(MAX) > 0
                || number.stripTrailingZeros().scale() > 2) {
            throw invalid(raw);
        }
        return number;
    }

    private static ApiException invalid(String raw) {
        return ApiException.badRequest("invalid_chapter_number", "'" + raw + "' is not a valid chapter number.");
    }
}
