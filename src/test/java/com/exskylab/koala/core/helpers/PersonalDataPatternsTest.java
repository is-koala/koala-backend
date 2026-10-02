package com.exskylab.koala.core.helpers;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonalDataPatternsTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "arama=ali.veli@example.com",
            "query=Ali@Example.COM.TR&durum=OPEN",
            "telefon=05321234567",
            "arama=0532 123 45 67",
            "arama=(0532) 123-45-67",
            "arama=+90 532 123 45 67",
            "arama=12345678901",
            "iban=TR330006100519786457841326",
            "iban=tr33 0006 1005 1978 6457 8413 26"
    })
    void personalDataIsRecognised(String filters) {
        assertTrue(PersonalDataPatterns.containsPersonalData(filters), filters);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "durum=CONFIRMED&from=2026-09-01T00:00:00Z&to=2026-09-30T23:59:59.999Z",
            "workerId=123e4567-e89b-12d3-a456-426614174000&employerId=00000000-0000-0000-0000-123456789012",
            "from=2026-09-01 to=2026-09-30",
            "sirala=createdAt,desc&durum=OPEN&durum=UNDER_REVIEW",
            "arama=kahve&neden=HARASSMENT",
            "tutar=1000.00",
            "status=TRANSFERRED"
    })
    void ordinaryFilterSummariesAreNotMistakenForPersonalData(String filters) {
        assertFalse(PersonalDataPatterns.containsPersonalData(filters), filters);
    }
}
