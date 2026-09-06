package com.exskylab.koala.core.helpers;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlainDecimalStringConverterTest {

    @Test
    void conversionPreservesScaleAndNeverUsesScientificNotation() {
        assertEquals("0.0750", PlainDecimalStringConverter.convert(new BigDecimal("0.0750")));
        assertEquals("1000", PlainDecimalStringConverter.convert(new BigDecimal("1E+3")));
        assertEquals("1050.11", PlainDecimalStringConverter.convert(new BigDecimal("1050.11")));
    }

    @Test
    void nullStaysNull() {
        assertNull(PlainDecimalStringConverter.convert(null));
    }
}
