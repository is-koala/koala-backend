package com.exskylab.koala.core.utilities.identity;

import java.nio.charset.StandardCharsets;
import java.util.Optional;


public final class AdditionalPersonalDetails {

    private static final int DG11_TAG = 0x6C;
    private static final int FULL_NAME_TAG = 0x5F0E;

    private final String surname;
    private final String givenNames;

    private AdditionalPersonalDetails(String surname, String givenNames) {
        this.surname = surname;
        this.givenNames = givenNames;
    }

    public static Optional<AdditionalPersonalDetails> parse(byte[] dataGroup11) {
        return findFullName(dataGroup11).flatMap(AdditionalPersonalDetails::split);
    }

    public String surname() {
        return surname;
    }

    public String givenNames() {
        return givenNames;
    }

    private static Optional<AdditionalPersonalDetails> split(String fullName) {
        String[] parts = fullName.split("<<", 2);
        if (parts.length != 2) {
            return Optional.empty();
        }

        String surname = clean(parts[0]);
        String givenNames = clean(parts[1]);

        return surname.isEmpty() || givenNames.isEmpty()
                ? Optional.empty()
                : Optional.of(new AdditionalPersonalDetails(surname, givenNames));
    }

    private static String clean(String value) {
        return value.replace('<', ' ').trim().replaceAll("\\s+", " ");
    }

    private static Optional<String> findFullName(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return Optional.empty();
        }

        int index = 0;
        if ((bytes[0] & 0xFF) == DG11_TAG) {
            index = contentStart(bytes, 0, 1);
            if (index < 0) {
                return Optional.empty();
            }
        }

        while (index < bytes.length - 1) {
            int tag = bytes[index] & 0xFF;
            int tagLength = (tag & 0x1F) == 0x1F ? 2 : 1;
            int tagValue = tagLength == 2
                    ? ((bytes[index] & 0xFF) << 8) | (bytes[index + 1] & 0xFF)
                    : tag;

            int start = contentStart(bytes, index, tagLength);
            if (start < 0) {
                return Optional.empty();
            }
            int length = contentLength(bytes, index, tagLength);
            if (length < 0 || start + length > bytes.length) {
                return Optional.empty();
            }

            if (tagValue == FULL_NAME_TAG) {
                return Optional.of(new String(bytes, start, length, StandardCharsets.UTF_8));
            }

            index = start + length;
        }

        return Optional.empty();
    }

    private static int contentStart(byte[] bytes, int offset, int tagLength) {
        int lengthOffset = offset + tagLength;
        if (lengthOffset >= bytes.length) {
            return -1;
        }
        int first = bytes[lengthOffset] & 0xFF;
        return first < 0x80 ? lengthOffset + 1 : lengthOffset + 1 + (first & 0x7F);
    }

    private static int contentLength(byte[] bytes, int offset, int tagLength) {
        int lengthOffset = offset + tagLength;
        int first = bytes[lengthOffset] & 0xFF;
        if (first < 0x80) {
            return first;
        }

        int count = first & 0x7F;
        if (count == 0 || count > 4 || lengthOffset + count >= bytes.length) {
            return -1;
        }

        int length = 0;
        for (int i = 1; i <= count; i++) {
            length = (length << 8) | (bytes[lengthOffset + i] & 0xFF);
        }
        return length;
    }
}
