package com.exskylab.koala.core.helpers;

import com.exskylab.koala.support.TestLatches;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonalDataPatternsTest {

    private static final int ONE_MEGABYTE = 1024 * 1024;

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
            "iban=tr33 0006 1005 1978 6457 8413 26",
            "arama=ali%40example.com",
            "arama=ali%2540example.com",
            "arama=0532%20123%2045%2067",
            "arama=0532+123+45+67",
            "iban=TR33%200006%201005%201978%206457%208413%2026"
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
            "status=TRANSFERRED",
            "from=2026-09-01T00%3A00%3A00Z&durum=CONFIRMED",
            "arama=%zz%",
            "arama=a@b",
            "arama=@example.com"
    })
    void ordinaryFilterSummariesAreNotMistakenForPersonalData(String filters) {
        assertFalse(PersonalDataPatterns.containsPersonalData(filters), filters);
    }

    @Test
    void aTenDigitNumberThatIsNotAnIdentifierOrADateIsAKnownFalsePositive() {
        assertTrue(PersonalDataPatterns.containsPersonalData("from=1727740800000"),
                "Epoch milisaniye 10+ rakamlık dizi olduğu için reddedilir; panel tarihleri ISO biçiminde göndermeli.");
        assertFalse(PersonalDataPatterns.containsPersonalData("from=2024-10-01T00:00:00Z"));
    }

    @Test
    void decodingIsLenientAndBounded() {
        assertEquals("ali@example.com", PersonalDataPatterns.decodeLeniently("ali%40example.com"));
        assertEquals("0532 123", PersonalDataPatterns.decodeLeniently("0532+123"));
        assertEquals("%zz%4", PersonalDataPatterns.decodeLeniently("%zz%4"));
        assertEquals("İş", PersonalDataPatterns.decodeLeniently("%C4%B0%C5%9F"));
        assertTrue(PersonalDataPatterns.containsPersonalData("ali%252540example.com"));
        assertFalse(PersonalDataPatterns.containsPersonalData("ali%25252540example.com"),
                "Çözme en çok üç tur sürer; daha derin kodlama bilinçli olarak çözülmez.");
    }

    @Test
    void adversarialInputsOfAMegabyteAreScannedInLinearTime() {
        List<String> adversarial = List.of(
                "a".repeat(ONE_MEGABYTE),
                "a@".repeat(ONE_MEGABYTE / 2),
                "a@a".repeat(ONE_MEGABYTE / 3),
                "a@a.".repeat(ONE_MEGABYTE / 4).replace(".", "&"),
                "1.".repeat(ONE_MEGABYTE / 2).replace("1.1", "1a1"),
                "TR1 ".repeat(ONE_MEGABYTE / 4),
                "TR12 a".repeat(ONE_MEGABYTE / 6),
                "%".repeat(ONE_MEGABYTE),
                "%4".repeat(ONE_MEGABYTE / 2),
                "+".repeat(ONE_MEGABYTE),
                "123456789a".repeat(ONE_MEGABYTE / 10));

        for (String input : adversarial) {
            assertTimeoutPreemptively(TestLatches.progressTimeout(),
                    () -> PersonalDataPatterns.containsPersonalData(input),
                    "Doğrusal olmayan tarama: " + input.substring(0, 12) + "…");
        }
    }
}
