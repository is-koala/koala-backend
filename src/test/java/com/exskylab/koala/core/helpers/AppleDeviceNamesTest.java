package com.exskylab.koala.core.helpers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AppleDeviceNamesTest {

    @Test
    void knownIdentifiersMapToMarketingNamesCaseInsensitively() {
        assertEquals("iPhone 14 Pro Max", AppleDeviceNames.marketingName("iPhone15,3"));
        assertEquals("iPhone 14 Pro Max", AppleDeviceNames.marketingName("IPHONE15,3"));
        assertEquals("iPad mini (6. nesil)", AppleDeviceNames.marketingName("iPad14,1"));
    }

    @Test
    void unknownIdentifiersComeBackUntouched() {
        assertEquals("iPhone99,9", AppleDeviceNames.marketingName("iPhone99,9"));
        assertEquals("SM-S928B", AppleDeviceNames.marketingName(" SM-S928B "));
        assertNull(AppleDeviceNames.marketingName(null));
    }
}
