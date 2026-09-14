package com.exskylab.koala.core.helpers;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class LogMasks {

    private static final String REDACTED = "***";

    private LogMasks() {
    }

    public static String email(String email) {
        if (email == null || email.isBlank()) {
            return REDACTED;
        }
        int at = email.indexOf('@');
        if (at < 1) {
            return REDACTED;
        }
        return email.charAt(0) + REDACTED + email.substring(at);
    }

    public static String phone(String phoneNumber) {
        if (phoneNumber == null) {
            return REDACTED;
        }
        String digits = phoneNumber.replaceAll("\\D", "");
        if (digits.length() < 4) {
            return REDACTED;
        }
        return REDACTED + digits.substring(digits.length() - 4);
    }

    public static String phones(String[] phoneNumbers) {
        if (phoneNumbers == null || phoneNumbers.length == 0) {
            return REDACTED;
        }
        StringBuilder masked = new StringBuilder();
        for (int i = 0; i < phoneNumbers.length; i++) {
            if (i > 0) {
                masked.append(", ");
            }
            masked.append(phone(phoneNumbers[i]));
        }
        return masked.toString();
    }

    public static String iban(String iban) {
        if (iban == null) {
            return REDACTED;
        }
        String normalized = iban.replaceAll("\\s", "");
        if (normalized.length() < 8) {
            return REDACTED;
        }
        return normalized.substring(0, 4) + REDACTED + normalized.substring(normalized.length() - 4);
    }

    public static String identityNumber(String identityNumber) {
        return REDACTED;
    }

    public static String maskedIdentityNumber(String identityNumber) {
        return identityNumber == null || identityNumber.isBlank() ? null : "***********";
    }

    public static String coordinates(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null) {
            return REDACTED;
        }
        return latitude.setScale(2, RoundingMode.HALF_UP).toPlainString() + ","
                + longitude.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
