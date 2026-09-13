package com.exskylab.koala.core.utilities.identity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class LdsFilesTest {

    private static final byte[] CONTENT = new byte[]{0x30, 0x03, 0x02, 0x01, 0x07};

    @Test
    void aShortFormLengthIsUnwrapped() {
        byte[] file = concat(new byte[]{0x6F, (byte) CONTENT.length}, CONTENT);

        assertArrayEquals(CONTENT, LdsFiles.unwrap(file, 0x6F));
    }

    @Test
    void aOneByteLongFormLengthIsUnwrapped() {
        byte[] file = concat(new byte[]{0x6F, (byte) 0x81, (byte) CONTENT.length}, CONTENT);

        assertArrayEquals(CONTENT, LdsFiles.unwrap(file, 0x6F));
    }

    @Test
    void aTwoByteLongFormLengthIsUnwrapped() {
        byte[] file = concat(new byte[]{0x6F, (byte) 0x82, 0x00, (byte) CONTENT.length}, CONTENT);

        assertArrayEquals(CONTENT, LdsFiles.unwrap(file, 0x6F));
    }

    @Test
    void aThreeByteLongFormLengthIsUnwrapped() {
        byte[] file = concat(new byte[]{0x6F, (byte) 0x83, 0x00, 0x00, (byte) CONTENT.length}, CONTENT);

        assertArrayEquals(CONTENT, LdsFiles.unwrap(file, 0x6F));
    }

    @Test
    void bytesBeyondTheDeclaredLengthAreDropped() {
        byte[] file = concat(concat(new byte[]{0x6F, (byte) 0x82, 0x00, (byte) CONTENT.length}, CONTENT),
                new byte[]{(byte) 0xFF, (byte) 0xFF});

        assertArrayEquals(CONTENT, LdsFiles.unwrap(file, 0x6F));
    }

    @Test
    void aDeclaredLengthLongerThanTheFileIsRefused() {
        byte[] file = concat(new byte[]{0x6F, (byte) 0x82, 0x01, 0x00}, CONTENT);

        assertThrows(IllegalArgumentException.class, () -> LdsFiles.unwrap(file, 0x6F));
    }

    @ParameterizedTest
    @ValueSource(ints = {0x80, 0x85, 0xFF})
    void indefiniteAndOversizedLengthFormsAreRefused(int lengthOctet) {
        byte[] file = concat(new byte[]{0x6F, (byte) lengthOctet, 0, 0, 0, 0, 0}, CONTENT);

        assertThrows(IllegalArgumentException.class, () -> LdsFiles.unwrap(file, 0x6F));
    }

    @Test
    void aFileWithoutTheExpectedTagIsReturnedAsIs() {
        assertSame(CONTENT, LdsFiles.unwrap(CONTENT, 0x6F));
    }

    @Test
    void aFileTooShortToCarryALengthIsReturnedAsIs() {
        byte[] file = new byte[]{0x6F};

        assertSame(file, LdsFiles.unwrap(file, 0x6F));
        assertNull(LdsFiles.unwrap(null, 0x6F));
    }

    private static byte[] concat(byte[] head, byte[] tail) {
        byte[] out = Arrays.copyOf(head, head.length + tail.length);
        System.arraycopy(tail, 0, out, head.length, tail.length);
        return out;
    }
}
