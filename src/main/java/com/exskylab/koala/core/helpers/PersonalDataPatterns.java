package com.exskylab.koala.core.helpers;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

public final class PersonalDataPatterns {

    public static final int MAX_DECODING_PASSES = 3;

    private static final Pattern UUID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private static final Pattern ISO_DATE_TIME = Pattern.compile(
            "\\d{4}-\\d{2}-\\d{2}(?:T\\d{2}:\\d{2}(?::\\d{2}(?:\\.\\d{1,9})?)?(?:Z|[+-]\\d{2}:?\\d{2})?)?");

    private static final List<Pattern> BOUNDED_PATTERNS = List.of(
            Pattern.compile("(?i)\\bTR\\s?+\\d{2}(?:\\s?+[0-9A-Z]){10}"),
            Pattern.compile("\\d(?:[\\s.()-]{0,2}+\\d){9}"));

    private PersonalDataPatterns() {
    }

    public static boolean containsPersonalData(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String candidate = text;
        for (int pass = 0; pass <= MAX_DECODING_PASSES; pass++) {
            if (matches(candidate)) {
                return true;
            }
            String decoded = decodeLeniently(candidate);
            if (decoded.equals(candidate)) {
                return false;
            }
            candidate = decoded;
        }
        return false;
    }

    static String decodeLeniently(String text) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(text.length());
        int index = 0;
        while (index < text.length()) {
            char current = text.charAt(index);
            if (current == '+') {
                bytes.write(' ');
                index++;
            } else if (current == '%' && index + 2 < text.length()
                    && isHex(text.charAt(index + 1)) && isHex(text.charAt(index + 2))) {
                bytes.write(Character.digit(text.charAt(index + 1), 16) * 16
                        + Character.digit(text.charAt(index + 2), 16));
                index += 3;
            } else {
                int codePoint = text.codePointAt(index);
                bytes.writeBytes(new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8));
                index += Character.charCount(codePoint);
            }
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private static boolean matches(String text) {
        String withoutIdentifiers = ISO_DATE_TIME.matcher(UUID.matcher(text).replaceAll(" ")).replaceAll(" ");
        return containsEmailAddress(withoutIdentifiers)
                || BOUNDED_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(withoutIdentifiers).find());
    }

    private static boolean containsEmailAddress(String text) {
        int from = 0;
        while (from < text.length()) {
            int at = text.indexOf('@', from);
            if (at < 0) {
                return false;
            }
            if (at > 0 && !isSeparator(text.charAt(at - 1)) && hasDottedDomain(text, at + 1)) {
                return true;
            }
            from = at + 1;
        }
        return false;
    }

    private static boolean hasDottedDomain(String text, int start) {
        boolean dotted = false;
        for (int index = start; index < text.length() && !isSeparator(text.charAt(index)); index++) {
            if (dotted) {
                return true;
            }
            if (text.charAt(index) == '.' && index > start) {
                dotted = true;
            }
        }
        return false;
    }

    private static boolean isSeparator(char character) {
        return Character.isWhitespace(character) || character == '@' || character == '&' || character == '=';
    }

    private static boolean isHex(char character) {
        return character >= '0' && character <= '9'
                || character >= 'a' && character <= 'f'
                || character >= 'A' && character <= 'F';
    }
}
