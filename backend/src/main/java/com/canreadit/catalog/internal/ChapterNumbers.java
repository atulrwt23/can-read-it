package com.canreadit.catalog.internal;

import com.canreadit.shared.ApiException;
import java.math.BigDecimal;
import java.util.regex.Pattern;

/** Chapter numbers are numeric(8,2): up to 999999.99, shown without trailing zeros. */
final class ChapterNumbers {

    /** Plain decimals only: no signs, exponents or more than two fraction digits. */
    private static final Pattern VALID = Pattern.compile("\\d{1,6}(\\.\\d{1,2})?");

    private ChapterNumbers() {}

    static String format(BigDecimal number) {
        return number.stripTrailingZeros().toPlainString();
    }

    static BigDecimal parse(String raw) {
        if (!VALID.matcher(raw).matches()) {
            throw ApiException.badRequest("invalid_chapter_number", "'" + raw + "' is not a valid chapter number.");
        }
        return new BigDecimal(raw);
    }
}
