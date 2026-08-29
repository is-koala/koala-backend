package com.exskylab.koala.core.helpers;

public final class AppVersion {

    private AppVersion() {
    }

    public static boolean isParsable(String version) {
        return numericPrefix(version) != null;
    }

    public static boolean isAtLeast(String candidate, String minimum) {
        int[] left = numericPrefix(candidate);
        int[] right = numericPrefix(minimum);

        if (left == null || right == null) {
            return true;
        }

        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            int a = i < left.length ? left[i] : 0;
            int b = i < right.length ? right[i] : 0;
            if (a != b) {
                return a > b;
            }
        }

        return true;
    }

    private static int[] numericPrefix(String version) {
        if (version == null || version.isBlank()) {
            return null;
        }

        String core = version.trim().split("[+\\-]", 2)[0];
        String[] parts = core.split("\\.");
        int[] numbers = new int[parts.length];

        for (int i = 0; i < parts.length; i++) {
            if (!parts[i].matches("\\d{1,9}")) {
                return null;
            }
            numbers[i] = Integer.parseInt(parts[i]);
        }

        return numbers.length == 0 ? null : numbers;
    }
}
