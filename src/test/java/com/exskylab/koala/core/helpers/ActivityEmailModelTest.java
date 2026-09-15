package com.exskylab.koala.core.helpers;

import com.exskylab.koala.core.constants.ActivityMessages;
import com.exskylab.koala.entities.Address;
import com.exskylab.koala.entities.City;
import com.exskylab.koala.entities.District;
import com.exskylab.koala.entities.Job;
import com.exskylab.koala.entities.JobAssignment;
import com.exskylab.koala.entities.NotificationTargetType;
import com.exskylab.koala.entities.User;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActivityEmailModelTest {

    private static final UUID ASSIGNMENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID JOB_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Test
    void wallClockIsFormattedInIstanbulWithoutTreatingItAsUtc() {
        LocalDateTime wallClock = LocalDateTime.of(2026, 9, 20, 9, 30);

        assertEquals("20 Eylül 2026, 09:30", ActivityEmailModel.formatWhen(wallClock, null));
        assertFalse("20 Eylül 2026, 12:30".equals(ActivityEmailModel.formatWhen(wallClock, null)));
    }

    @Test
    void anInstantIsConvertedThroughEuropeIstanbul() {
        Instant instant = LocalDateTime.of(2026, 9, 20, 9, 30)
                .atZone(ActivityEmailModel.ZONE)
                .toInstant();

        assertEquals("20 Eylül 2026, 09:30", ActivityEmailModel.formatWhen(null, instant));
        assertFalse("20 Eylül 2026, 09:30".equals(
                LocalDateTime.ofInstant(instant, ZoneOffset.UTC)
                        .format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm",
                                java.util.Locale.of("tr", "TR")))));
    }

    @Test
    void placeIsDistrictAndCityWithoutTheThoroughfare() {
        City city = new City();
        city.setName("İstanbul");
        District district = new District();
        district.setName("Kadıköy");
        Address address = new Address();
        address.setCity(city);
        address.setDistrict(district);
        address.setThoroughfare("Moda Caddesi");
        address.setBuildingNumber("12");

        assertEquals("Kadıköy, İstanbul", ActivityEmailModel.formatWhere(address));
        assertFalse(ActivityEmailModel.formatWhere(address).contains("Moda"));
    }

    @Test
    void tryAmountUsesTurkishGroupingAndTheLiraSign() {
        assertEquals("1.250,00 ₺", ActivityEmailModel.formatAmount(new BigDecimal("1250.00"), "TRY"));
        assertEquals("1.250,00 EUR", ActivityEmailModel.formatAmount(new BigDecimal("1250.00"), "EUR"));
        assertNull(ActivityEmailModel.formatAmount(null, "TRY"));
    }

    @Test
    void assignmentAmountPrefersTheLockedPayoutThenTheJobQuote() {
        Job job = new Job();
        job.setSalary(new BigDecimal("900.00"));
        job.setWorkerPayout(new BigDecimal("1100.00"));
        job.setCurrency("TRY");

        JobAssignment assignment = new JobAssignment();
        assignment.setJob(job);
        assertEquals(new BigDecimal("1100.00"), ActivityEmailModel.amountOf(assignment));

        assignment.setWorkerPayout(new BigDecimal("1250.00"));
        assignment.setCurrency("TRY");
        assertEquals(new BigDecimal("1250.00"), ActivityEmailModel.amountOf(assignment));
        assertEquals("TRY", ActivityEmailModel.currencyOf(assignment));
    }

    @Test
    void workerAssignmentOpensTheWorkerJobPageNotTheSiteRoot() {
        String url = ActivityEmailModel.actionUrl("https://iskoala.com/", NotificationTargetType.JOB_ASSIGNMENT,
                ASSIGNMENT_ID, JOB_ID, true);

        assertEquals("https://iskoala.com/panel/is/" + ASSIGNMENT_ID, url);
        assertFalse(url.equals("https://iskoala.com"));
        assertFalse(url.equals("https://iskoala.com/"));
    }

    @Test
    void employerAssignmentOpensTheEmployerAssignmentPage() {
        assertEquals("https://iskoala.com/isveren/atama/" + ASSIGNMENT_ID,
                ActivityEmailModel.actionUrl("https://iskoala.com", NotificationTargetType.JOB_ASSIGNMENT,
                        ASSIGNMENT_ID, JOB_ID, false));
    }

    @Test
    void unknownActivityTypeUsesTheNeutralFallback() {
        assertEquals("Hesabınızla ilgili yeni bir güncelleme var.",
                ActivityEmailModel.body("{activity.something.uninvented}", "resolved one-liner",
                        "Hesabınızla ilgili yeni bir güncelleme var."));
        assertEquals("İş tamamlandı olarak işaretlendi.",
                ActivityEmailModel.body(ActivityMessages.WORK_COMPLETED, "İş tamamlandı olarak işaretlendi.",
                        "Hesabınızla ilgili yeni bir güncelleme var."));
    }

    @Test
    void unknownOrMissingTargetsLandOnNotificationsNeverTheSiteRoot() {
        assertEquals("/panel/bildirimler",
                ActivityEmailModel.actionUrl("", null, null, null, false));
        assertEquals("https://iskoala.com/panel/bildirimler",
                ActivityEmailModel.actionUrl("https://iskoala.com", NotificationTargetType.CONVERSATION,
                        ASSIGNMENT_ID, JOB_ID, true));
        assertEquals("https://iskoala.com/panel/profil",
                ActivityEmailModel.actionUrl("https://iskoala.com", NotificationTargetType.USER,
                        UUID.randomUUID(), null, false));
    }

    @Test
    void counterpartNameJoinsFirstAndLast() {
        User user = new User();
        user.setFirstName("Ayşe");
        user.setLastName("Yılmaz");
        assertEquals("Ayşe Yılmaz", ActivityEmailModel.formatWho(user));
        assertNull(ActivityEmailModel.formatWho(null));
    }

    @Test
    void typicalActivityEmailParametersDoNotFitVarchar255() {
        User recipient = new User();
        recipient.setFirstName("Ayşe");
        String json = new ObjectMapper().writeValueAsString(ActivityEmailModel.parameters(
                recipient,
                "Düğün fotoğrafı — Kadıköy stüdyo çekimi",
                "İş tamamlandı olarak işaretlendi.",
                "https://iskoala.com/panel/is/" + ASSIGNMENT_ID,
                "20 Eylül 2026, 09:30",
                "Kadıköy, İstanbul",
                "Mehmet Yılmaz",
                "1.250,00 ₺"));
        assertTrue(json.length() > 255, json);
    }
}
