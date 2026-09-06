package com.exskylab.koala.core.helpers;

public final class IbanValidator {

    private static final int TURKISH_IBAN_LENGTH = 26;

    private IbanValidator() {
    }

    public static boolean isValid(String iban) {
        if (iban == null) {
            return false;
        }

        String normalized = iban.replaceAll("\\s", "");

        if (normalized.length() != TURKISH_IBAN_LENGTH) {
            return false;
        }

        if (!normalized.startsWith("TR")) {
            return false;
        }

        String reordered = normalized.substring(4) + normalized.substring(0, 4);

        StringBuilder numeric = new StringBuilder();
        for (char ch : reordered.toCharArray()) {
            if (Character.isUpperCase(ch)) {
                numeric.append(ch - 'A' + 10);
            } else if (Character.isDigit(ch)) {
                numeric.append(ch);
            } else {
                return false;
            }
        }

        int remainder = 0;
        for (int i = 0; i < numeric.length(); i++) {
            remainder = (remainder * 10 + (numeric.charAt(i) - '0')) % 97;
        }

        return remainder == 1;
    }

    public static String normalize(String iban) {
        return iban == null ? null : iban.replaceAll("\\s", "");
    }
}
