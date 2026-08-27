package com.exskylab.koala.core.utilities.storage;

import java.nio.charset.StandardCharsets;

public final class FileDispositions {

    private static final String UNRESERVED = "!#$&+-.^_`|~";

    private FileDispositions() {
    }

    public static String inline(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            return "inline";
        }

        String trimmed = originalFileName.trim();

        return "inline; filename=\"" + StorageKeys.sanitize(trimmed) + "\""
                + "; filename*=UTF-8''" + encode(trimmed);
    }

    private static String encode(String value) {
        StringBuilder builder = new StringBuilder();

        for (byte raw : value.getBytes(StandardCharsets.UTF_8)) {
            int unsigned = raw & 0xFF;
            char character = (char) unsigned;

            if (unsigned < 128 && (Character.isLetterOrDigit(character) || UNRESERVED.indexOf(character) >= 0)) {
                builder.append(character);
            } else {
                builder.append('%').append(String.format("%02X", unsigned));
            }
        }

        return builder.toString();
    }
}
