package com.exskylab.koala.core.utilities.identity;

import java.util.Arrays;

public final class LdsFiles {

    private static final int LONG_FORM = 0x80;
    private static final int MAX_LENGTH_OCTETS = 3;

    private LdsFiles() {
    }

    public static byte[] unwrap(byte[] file, int tag) {
        if (file == null || file.length < 2 || (file[0] & 0xFF) != tag) {
            return file;
        }

        int first = file[1] & 0xFF;
        int offset;
        int length;

        if (first < LONG_FORM) {
            offset = 2;
            length = first;
        } else {
            int octets = first & 0x7F;
            if (octets == 0 || octets > MAX_LENGTH_OCTETS || file.length < 2 + octets) {
                throw new IllegalArgumentException("unsupported BER length form 0x" + Integer.toHexString(first));
            }
            length = 0;
            for (int i = 0; i < octets; i++) {
                length = (length << 8) | (file[2 + i] & 0xFF);
            }
            offset = 2 + octets;
        }

        if (length < 0 || length > file.length - offset) {
            throw new IllegalArgumentException("BER length " + length + " exceeds the " + file.length + " byte file");
        }

        return Arrays.copyOfRange(file, offset, offset + length);
    }
}
