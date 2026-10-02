package com.exskylab.koala.core.helpers;

import java.util.List;
import java.util.regex.Pattern;

public final class PersonalDataPatterns {

    private static final Pattern UUID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private static final Pattern ISO_DATE_TIME = Pattern.compile(
            "\\d{4}-\\d{2}-\\d{2}(?:T\\d{2}:\\d{2}(?::\\d{2}(?:\\.\\d{1,9})?)?(?:Z|[+-]\\d{2}:?\\d{2})?)?");

    private static final List<Pattern> PERSONAL_DATA = List.of(
            Pattern.compile("[^\\s@&=]+@[^\\s@&=]+\\.[^\\s@&=]+"),
            Pattern.compile("(?i)\\bTR\\s?\\d{2}(?:\\s?[0-9A-Z]){10,}"),
            Pattern.compile("(?:\\d[\\s.()-]{0,2}){10,}"));

    private PersonalDataPatterns() {
    }

    public static boolean containsPersonalData(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String withoutIdentifiers = ISO_DATE_TIME.matcher(UUID.matcher(text).replaceAll(" ")).replaceAll(" ");
        return PERSONAL_DATA.stream().anyMatch(pattern -> pattern.matcher(withoutIdentifiers).find());
    }
}
