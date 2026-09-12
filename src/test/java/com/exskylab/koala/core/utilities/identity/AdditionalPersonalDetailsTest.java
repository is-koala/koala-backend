package com.exskylab.koala.core.utilities.identity;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdditionalPersonalDetailsTest {

    @Test
    void anIcaoTaggedDg11YieldsTheNationalName() {
        byte[] dg11 = wrap(0x6B, "ÇAĞRI", "İLKER");

        AdditionalPersonalDetails name = AdditionalPersonalDetails.parse(dg11).orElseThrow();

        assertEquals("ÇAĞRI", name.surname());
        assertEquals("İLKER", name.givenNames());
    }

    @Test
    void aDg12TagIsNotAcceptedAsDg11() {
        byte[] dg12DisguisedAsDg11 = wrap(0x6C, "ÇAĞRI", "İLKER");

        assertTrue(AdditionalPersonalDetails.parse(dg12DisguisedAsDg11).isEmpty());
    }

    private static byte[] wrap(int outerTag, String surname, String givenNames) {
        byte[] name = (surname + "<<" + givenNames).getBytes(StandardCharsets.UTF_8);
        byte[] inner = new byte[3 + name.length];
        inner[0] = 0x5F;
        inner[1] = 0x0E;
        inner[2] = (byte) name.length;
        System.arraycopy(name, 0, inner, 3, name.length);

        byte[] outer = new byte[2 + inner.length];
        outer[0] = (byte) outerTag;
        outer[1] = (byte) inner.length;
        System.arraycopy(inner, 0, outer, 2, inner.length);
        return outer;
    }
}
