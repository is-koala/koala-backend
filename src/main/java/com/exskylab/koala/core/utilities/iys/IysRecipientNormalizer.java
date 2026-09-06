package com.exskylab.koala.core.utilities.iys;

import com.exskylab.koala.entities.NotificationChannel;

public final class IysRecipientNormalizer {

    private IysRecipientNormalizer() {
    }

    public static String normalize(String recipient, NotificationChannel channel) {
        if (recipient == null || recipient.isBlank()) {
            return null;
        }
        if (channel == NotificationChannel.EMAIL) {
            return recipient.strip();
        }
        if (channel != NotificationChannel.SMS) {
            return null;
        }

        String digits = recipient.replaceAll("\\D", "");
        String national;
        if (digits.startsWith("0090") && digits.length() == 14) {
            national = digits.substring(4);
        } else if (digits.startsWith("90") && digits.length() == 12) {
            national = digits.substring(2);
        } else if (digits.startsWith("0") && digits.length() == 11) {
            national = digits.substring(1);
        } else if (digits.length() == 10) {
            national = digits;
        } else {
            return null;
        }
        return national.matches("5\\d{9}") ? "+90" + national : null;
    }
}
