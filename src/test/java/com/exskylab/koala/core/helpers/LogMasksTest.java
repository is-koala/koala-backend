package com.exskylab.koala.core.helpers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LogMasksTest {

    @Test
    void theEmailDomainSurvivesButTheLocalPartDoesNot() {
        assertEquals("y***@gmail.com", LogMasks.email("yusuf@gmail.com"));
        assertEquals("a***@std.yildiz.edu.tr", LogMasks.email("ada.yilmaz@std.yildiz.edu.tr"));
    }

    @Test
    void aMalformedEmailIsFullyRedacted() {
        assertEquals("***", LogMasks.email(null));
        assertEquals("***", LogMasks.email(""));
        assertEquals("***", LogMasks.email("@gmail.com"));
        assertEquals("***", LogMasks.email("duz-metin"));
    }

    @Test
    void onlyTheLastFourDigitsOfAPhoneRemain() {
        assertEquals("***4567", LogMasks.phone("+90 532 123 4567"));
        assertEquals("***4567", LogMasks.phone("05321234567"));
        assertEquals("***", LogMasks.phone("12"));
        assertEquals("***", LogMasks.phone(null));
    }

    @Test
    void anIbanKeepsOnlyItsPrefixAndSuffix() {
        String masked = LogMasks.iban("TR18 0006 2001 1900 0006 6723 15");

        assertEquals("TR18***2315", masked);
        assertFalse(masked.contains("620011900000"), "IBAN govdesi loga sizmamali.");
    }

    @Test
    void aNationalIdIsNeverPartiallyRevealed() {
        assertEquals("***", LogMasks.identityNumber("12345678901"));
        assertEquals("***", LogMasks.identityNumber(null));
    }

    @Test
    void aPhoneListIsMaskedElementByElement() {
        assertEquals("***4567, ***8901", LogMasks.phones(new String[]{"05321234567", "05339998901"}));
        assertEquals("***", LogMasks.phones(new String[]{}));
        assertEquals("***", LogMasks.phones(null));
    }
}
