package com.exskylab.koala.core.dtos;

import com.exskylab.koala.core.dtos.report.response.AdminReportDetailDto;
import com.exskylab.koala.core.dtos.report.response.AdminReportDto;
import com.exskylab.koala.core.dtos.report.response.ReportContentDto;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportModerationPrivacyTest {

    private static final List<String> DIRECT_IDENTIFIERS = List.of(
            "email", "phone", "iban", "password", "identitynumber", "documentnumber", "birthdate");

    private record LeakyContact(String email) {
    }

    private record LeakyEnvelope(UUID id, LeakyContact contact) {
    }

    private static List<String> namesReachableFrom(Class<?> type) {
        List<String> names = new ArrayList<>();
        collect(type, new HashSet<>(), names);
        return names;
    }

    private static void collect(Class<?> type, Set<Class<?>> visited, List<String> names) {
        if (type == null || !visited.add(type) || !type.getName().startsWith("com.exskylab.koala")) {
            return;
        }

        if (type.isRecord()) {
            for (RecordComponent component : type.getRecordComponents()) {
                names.add(component.getName());
                collect(component.getType(), visited, names);
            }
            return;
        }

        for (Field field : type.getDeclaredFields()) {
            if (field.isSynthetic()) {
                continue;
            }
            names.add(field.getName());
            collect(field.getType(), visited, names);
        }
    }

    private static List<String> personalIdentifiersIn(Class<?> type) {
        return namesReachableFrom(type).stream()
                .filter(name -> {
                    String lowered = name.toLowerCase(Locale.ROOT);
                    return DIRECT_IDENTIFIERS.stream().anyMatch(lowered::contains);
                })
                .toList();
    }

    @Test
    void theModerationQueueNeverShipsADirectIdentifier() {
        assertEquals(List.of(), personalIdentifiersIn(AdminReportDto.class),
                "Kuyruk satiri kisiyi SafeUserDto ile adlandirir; e-posta, telefon, TCKN ya da IBAN "
                        + "moderasyon karari icin gerekli degildir ve tel uzerinden gecmemelidir.");
    }

    @Test
    void theReportDetailNeverShipsADirectIdentifierEither() {
        assertEquals(List.of(), personalIdentifiersIn(AdminReportDetailDto.class));
        assertEquals(List.of(), personalIdentifiersIn(ReportContentDto.class));
    }

    @Test
    void theScannerCanActuallyGoRed() {
        assertEquals(List.of("email"), personalIdentifiersIn(LeakyContact.class),
                "Tarama dogrudan tasinan bir e-postayi gormeli.");

        assertTrue(personalIdentifiersIn(LeakyEnvelope.class).contains("email"),
                "Tarama ic ice gecmis bir DTO'daki e-postayi da gormeli; "
                        + "yoksa SafeUserDto zenginlestirildiginde sessizce yesil kalir.");
    }
}
