package com.exskylab.koala.core.dtos;

import com.exskylab.koala.core.dtos.availability.request.SaveAvailabilityDto;
import com.exskylab.koala.core.dtos.badge.request.SaveBadgeDto;
import com.exskylab.koala.core.dtos.education.request.SaveEducationDto;
import com.exskylab.koala.core.dtos.experience.request.SaveExperienceDto;
import com.exskylab.koala.core.dtos.jobAlert.request.SaveJobAlertDto;
import com.exskylab.koala.core.dtos.report.request.CreateReportDto;
import com.exskylab.koala.entities.*;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class RequestValidationTest {

    private final Validator validator;

    RequestValidationTest() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    private Set<String> messagesFor(Object dto) {
        return validator.validate(dto).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    private boolean isValid(Object dto) {
        return validator.validate(dto).isEmpty();
    }

    @Test
    void aReportMustPointAtAUserAJobOrAMessage() {
        CreateReportDto empty = new CreateReportDto();
        empty.setReason(ReportReason.SPAM);

        assertTrue(messagesFor(empty).contains("{report.target.required}"));

        empty.setJobId(UUID.randomUUID());
        assertTrue(isValid(empty));
    }

    @Test
    void aCountBadgeWithoutItsThresholdIsRejected() {
        SaveBadgeDto badge = new SaveBadgeDto();
        badge.setCode("YENI_ROZET");
        badge.setFamily(BadgeFamily.OTHER);
        badge.setTier(BadgeTier.BRONZE);
        badge.setName("Yeni");
        badge.setRuleType(BadgeRuleType.COMPLETED_ASSIGNMENTS);

        assertTrue(messagesFor(badge).contains("{badge.min.count.required}"));

        badge.setMinCount(5);
        assertTrue(isValid(badge));
    }

    @Test
    void aRatingBadgeNeedsBothTheAverageAndTheSampleSize() {
        SaveBadgeDto badge = new SaveBadgeDto();
        badge.setCode("YUKSEK_PUAN");
        badge.setFamily(BadgeFamily.RATING);
        badge.setTier(BadgeTier.GOLD);
        badge.setName("Yüksek puan");
        badge.setRuleType(BadgeRuleType.AVERAGE_RATING_WITH_MINIMUM_REVIEWS);
        badge.setMinAverageRating(new BigDecimal("4.50"));

        assertTrue(messagesFor(badge).contains("{badge.rating.threshold.required}"),
                "yalnızca ortalama verilmiş, minimum yorum sayısı yok");

        badge.setMinReviewCount(10);
        assertTrue(isValid(badge));
    }

    @Test
    void anIdentityBadgeNeedsNoThreshold() {
        SaveBadgeDto badge = new SaveBadgeDto();
        badge.setCode("KIMLIK");
        badge.setFamily(BadgeFamily.IDENTITY);
        badge.setTier(BadgeTier.NONE);
        badge.setName("Kimlik doğrulandı");
        badge.setRuleType(BadgeRuleType.IDENTITY_VERIFIED);

        assertTrue(isValid(badge));
    }

    @Test
    void anEducationCannotEndBeforeItStarts() {
        SaveEducationDto education = new SaveEducationDto();
        education.setUniversityId(UUID.randomUUID());
        education.setFacultyId(UUID.randomUUID());
        education.setDepartmentId(UUID.randomUUID());
        education.setType(GradeType.values()[0]);
        education.setStartDate(LocalDate.of(2021, 1, 1));
        education.setEndDate(LocalDate.of(2020, 1, 1));

        assertTrue(messagesFor(education).contains("{education.end.before.start}"));
    }

    @Test
    void anOngoingEducationHasNoEndDateAndIsStillValid() {
        SaveEducationDto education = new SaveEducationDto();
        education.setUniversityId(UUID.randomUUID());
        education.setFacultyId(UUID.randomUUID());
        education.setDepartmentId(UUID.randomUUID());
        education.setType(GradeType.values()[0]);
        education.setStartDate(LocalDate.of(2021, 1, 1));

        assertTrue(isValid(education));
    }

    @Test
    void anExperienceCannotStartInTheFutureOrEndBeforeItStarts() {
        SaveExperienceDto experience = new SaveExperienceDto();
        experience.setTitle("Barista");
        experience.setStartDate(LocalDate.now().plusDays(1));

        assertTrue(messagesFor(experience).contains("{experience.start.in.future}"));

        experience.setStartDate(LocalDate.of(2021, 1, 1));
        experience.setEndDate(LocalDate.of(2020, 1, 1));
        assertTrue(messagesFor(experience).contains("{experience.end.before.start}"));
    }

    @Test
    void anAlertNeedsAtLeastOneCriterion() {
        SaveJobAlertDto alert = new SaveJobAlertDto();
        alert.setAlertName("Her şey");

        assertTrue(messagesFor(alert).contains("{job.alert.criteria.required}"));

        alert.setCityName("İstanbul");
        assertTrue(isValid(alert));
    }

    @Test
    void anAvailabilitySlotMustEndAfterItStarts() {
        SaveAvailabilityDto availability = new SaveAvailabilityDto(List.of(
                new SaveAvailabilityDto.Slot(DayOfWeek.MONDAY, LocalTime.of(17, 0), LocalTime.of(9, 0))));

        assertTrue(messagesFor(availability).contains("{availability.end.before.start}"));

        SaveAvailabilityDto ordered = new SaveAvailabilityDto(List.of(
                new SaveAvailabilityDto.Slot(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0))));
        assertTrue(isValid(ordered));
    }

    @Test
    void everyCrossFieldRuleReportsAMessageKeyNotARawSentence() {
        SaveBadgeDto badge = new SaveBadgeDto();
        badge.setRuleType(BadgeRuleType.COMPLETED_ASSIGNMENTS);

        assertTrue(messagesFor(badge).stream().allMatch(m -> m.startsWith("{") && m.endsWith("}")),
                "doğrulama mesajları çözümlenebilir anahtar olmalı: " + messagesFor(badge));
    }
}
