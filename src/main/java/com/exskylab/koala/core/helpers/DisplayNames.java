package com.exskylab.koala.core.helpers;

import java.util.Objects;
import java.util.stream.Stream;

public final class DisplayNames {

    private DisplayNames() {
    }

    public static String of(String firstName, String lastName) {
        String name = String.join(" ", Stream.of(firstName, lastName)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .toList());
        return name.isEmpty() ? null : name;
    }
}
