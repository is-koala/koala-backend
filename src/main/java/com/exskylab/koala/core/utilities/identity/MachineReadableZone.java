package com.exskylab.koala.core.utilities.identity;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Optional;

public final class MachineReadableZone {


    private static final char FILLER = '<';
    private static final int TD1_LINE_LENGTH = 30;
    private static final int TD1_TOTAL_LENGTH = TD1_LINE_LENGTH * 3;

    private final String raw;

    private MachineReadableZone(String raw) {
        this.raw = raw;
    }

    public static Optional<MachineReadableZone> fromDataGroup1(byte[] dataGroup1) {
        return extractMrz(dataGroup1)
                .map(mrz -> mrz.replaceAll("[\\r\\n\\s]", ""))
                .filter(mrz -> mrz.length() == TD1_TOTAL_LENGTH)
                .map(MachineReadableZone::new);
    }

    public Optional<String> nationalIdentityNumber() {
        if (raw.charAt(14) == FILLER) {
            return Optional.empty();
        }

        String optionalData = raw.substring(15, TD1_LINE_LENGTH).replace(String.valueOf(FILLER), "");

        return optionalData.matches("\\d{11}") ? Optional.of(optionalData) : Optional.empty();
    }


    public boolean matchesHolder(String firstName, String lastName) {
        String nameLine = raw.substring(TD1_LINE_LENGTH * 2);
        String[] parts = nameLine.split("<<", 2);

        String mrzSurname = normalize(parts[0]);
        String mrzGivenNames = normalize(parts.length > 1 ? parts[1] : "");

        return prefixCompatible(mrzSurname, normalize(lastName))
                && prefixCompatible(mrzGivenNames, normalize(firstName));
    }

    private static boolean prefixCompatible(String fromCard, String claimed) {
        if (fromCard.isEmpty() || claimed.isEmpty()) {
            return false;
        }
        return fromCard.startsWith(claimed) || claimed.startsWith(fromCard);
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }

        String folded = value
                .replace('ı', 'i').replace('İ', 'I')
                .replace('ğ', 'g').replace('Ğ', 'G')
                .replace('ü', 'u').replace('Ü', 'U')
                .replace('ş', 's').replace('Ş', 'S')
                .replace('ö', 'o').replace('Ö', 'O')
                .replace('ç', 'c').replace('Ç', 'C')
                .toUpperCase(java.util.Locale.ROOT);

        return folded.replaceAll("[^A-Z]", "");
    }

    public Optional<LocalDate> birthDate() {
        String yymmdd = raw.substring(TD1_LINE_LENGTH, TD1_LINE_LENGTH + 6);
        if (!yymmdd.matches("\\d{6}")) {
            return Optional.empty();
        }

        int year = Integer.parseInt(yymmdd.substring(0, 2));
        int month = Integer.parseInt(yymmdd.substring(2, 4));
        int day = Integer.parseInt(yymmdd.substring(4, 6));

        int century = year > (LocalDate.now().getYear() % 100) ? 1900 : 2000;
        try {
            return Optional.of(LocalDate.of(century + year, month, day));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static Optional<String> extractMrz(byte[] dataGroup1) {
        if (dataGroup1 == null || dataGroup1.length == 0) {
            return Optional.empty();
        }

        int index = 0;
        if ((dataGroup1[0] & 0xFF) == DataGroupTags.DG1) {
            index = skipTagAndLength(dataGroup1, 0, 1);
            if (index < 0) {
                return Optional.empty();
            }
        }

        if (index + 1 < dataGroup1.length
                && (dataGroup1[index] & 0xFF) == 0x5F && (dataGroup1[index + 1] & 0xFF) == 0x1F) {
            int contentStart = skipTagAndLength(dataGroup1, index, 2);
            if (contentStart < 0) {
                return Optional.empty();
            }
            return Optional.of(new String(dataGroup1, contentStart,
                    dataGroup1.length - contentStart, StandardCharsets.US_ASCII));
        }

        return Optional.of(new String(dataGroup1, StandardCharsets.US_ASCII));
    }

    private static int skipTagAndLength(byte[] bytes, int offset, int tagLength) {
        int lengthOffset = offset + tagLength;
        if (lengthOffset >= bytes.length) {
            return -1;
        }

        int first = bytes[lengthOffset] & 0xFF;
        if (first < 0x80) {
            return lengthOffset + 1;
        }

        int lengthBytes = first & 0x7F;
        return lengthOffset + 1 + lengthBytes <= bytes.length ? lengthOffset + 1 + lengthBytes : -1;
    }
}
