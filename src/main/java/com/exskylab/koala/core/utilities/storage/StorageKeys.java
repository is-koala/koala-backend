package com.exskylab.koala.core.utilities.storage;

import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

public final class StorageKeys {

    public static final String IMAGE_PREFIX = "images/";
    public static final String PUBLIC_FILE_PREFIX = "public/";
    public static final String PRIVATE_PREFIX = "files/";

    private static final String TURKISH = "çÇğĞıİöÖşŞüÜâÂîÎûÛ";
    private static final String ASCII = "cCgGiIoOsSuUaAiIuU";
    private static final int MAX_EXTENSION_LENGTH = 10;

    private StorageKeys() {
    }

    public static String newKey(FolderType folderType, String originalFileName) {
        return prefix(folderType) + UUID.randomUUID() + extensionOf(originalFileName);
    }

    public static String prefix(FolderType folderType) {
        return switch (folderType) {
            case IMAGE -> IMAGE_PREFIX;
            case PUBLIC_FILE -> PUBLIC_FILE_PREFIX;
            case FILE -> PRIVATE_PREFIX;
        };
    }

    public static boolean isPrivate(String key) {
        return key != null && key.startsWith(PRIVATE_PREFIX);
    }

    public static boolean isPublicFile(String key) {
        return key != null && key.startsWith(PUBLIC_FILE_PREFIX);
    }

    public static boolean isImage(String key) {
        return key != null && key.startsWith(IMAGE_PREFIX);
    }

    public static boolean isKnownPrefix(String key) {
        return isImage(key) || isPublicFile(key) || isPrivate(key);
    }

    public static String fromRequestPath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return rawPath;
        }
        return UriUtils.decode(rawPath, StandardCharsets.UTF_8);
    }

    static String extensionOf(String originalFileName) {
        if (originalFileName == null) {
            return "";
        }

        int dot = originalFileName.lastIndexOf('.');
        if (dot < 0 || dot == originalFileName.length() - 1) {
            return "";
        }

        String extension = originalFileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (extension.length() > MAX_EXTENSION_LENGTH || !extension.chars().allMatch(Character::isLetterOrDigit)) {
            return "";
        }

        return "." + extension;
    }

    public static String sanitize(String originalFileName) {
        if (originalFileName == null) {
            return "file";
        }

        StringBuilder builder = new StringBuilder(originalFileName.length());

        for (char character : originalFileName.toCharArray()) {
            int index = TURKISH.indexOf(character);
            char mapped = index >= 0 ? ASCII.charAt(index) : character;

            if (mapped == '.' || mapped == '-' || mapped == '_'
                    || (mapped >= 'a' && mapped <= 'z')
                    || (mapped >= 'A' && mapped <= 'Z')
                    || (mapped >= '0' && mapped <= '9')) {
                builder.append(mapped);
            } else {
                builder.append('_');
            }
        }

        String collapsed = builder.toString()
                .replaceAll("_+", "_")
                .replaceAll("\\.{2,}", ".")
                .replaceAll("^[._]+", "");

        return collapsed.isBlank() || "_".equals(collapsed) ? "file" : collapsed;
    }
}
