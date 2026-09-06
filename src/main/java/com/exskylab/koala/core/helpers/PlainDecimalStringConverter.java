package com.exskylab.koala.core.helpers;

import java.math.BigDecimal;

public final class PlainDecimalStringConverter {

    private PlainDecimalStringConverter() {
    }

    public static String convert(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}
