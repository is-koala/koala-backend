package com.exskylab.koala.core.dtos;

import com.exskylab.koala.core.dtos.availability.request.SaveAvailabilityDto;
import com.exskylab.koala.core.dtos.address.request.AddressComponentsRequestDto;
import com.exskylab.koala.core.dtos.address.request.AddressPositionRequestDto;
import com.exskylab.koala.core.dtos.address.request.AddressWriteDto;
import com.exskylab.koala.core.dtos.address.request.ConfirmedPositionSource;
import com.exskylab.koala.core.dtos.badge.request.SaveBadgeDto;
import com.exskylab.koala.core.dtos.companyContact.request.InviteContactToCompanyDto;
import com.exskylab.koala.core.dtos.education.request.SaveEducationDto;
import com.exskylab.koala.core.constants.IdentityMessages;
import com.exskylab.koala.core.dtos.experience.request.SaveExperienceDto;
import com.exskylab.koala.core.dtos.identity.request.RejectIdentityReviewDto;
import com.exskylab.koala.core.dtos.jobAlert.request.SaveJobAlertDto;
import com.exskylab.koala.core.dtos.notificationPreference.request.NotificationPreferenceEntryDto;
import com.exskylab.koala.core.dtos.notificationPreference.request.UpdateNotificationPreferencesDto;
import com.exskylab.koala.core.dtos.report.request.CreateReportDto;
import com.exskylab.koala.core.dtos.report.request.ReviewReportDto;
import com.exskylab.koala.core.dtos.timesheet.request.CheckPointDto;
import com.exskylab.koala.entities.*;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Instant;
import java.util.Arrays;
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
    void aContactInvitationNamesTheEmailFieldAndRejectsAMalformedAddress() {
        InviteContactToCompanyDto invite = new InviteContactToCompanyDto();
        invite.setRole(CompanyContactRole.STAFF.name());

        assertTrue(messagesFor(invite).contains("{companyContact.email.notBlank}"));
        assertFalse(messagesFor(invite).contains("{companyContact.firstName.notBlank}"));

        invite.setEmail("bu-bir-eposta-degil");
        assertTrue(messagesFor(invite).contains("{companyContact.email.invalid}"));

        invite.setEmail("davetli@ornek.test");
        assertTrue(isValid(invite), "Davet yalniz e-posta ve rol ile gonderilebilmeli: " + messagesFor(invite));
    }

    @Test
    void aContactFormRequiresTopicNameEmailMessageAndConsent() {
        com.exskylab.koala.core.dtos.contact.request.ContactRequestDto request =
                new com.exskylab.koala.core.dtos.contact.request.ContactRequestDto();

        assertTrue(messagesFor(request).contains("{contact.topic.required}"));
        assertTrue(messagesFor(request).contains("{contact.name.required}"));
        assertTrue(messagesFor(request).contains("{contact.email.required}"));
        assertTrue(messagesFor(request).contains("{contact.message.required}"));
        assertTrue(messagesFor(request).contains("{contact.consent.required}"));

        request.setTopic("destek");
        request.setName("Ada Yılmaz");
        request.setEmail("ada@example.test");
        request.setMessage("Hesap silme talebim görünmüyor.");
        request.setConsent(true);
        assertTrue(isValid(request), "geçerli iletişim formu: " + messagesFor(request));

        request.setTopic("not-a-topic");
        assertTrue(messagesFor(request).contains("{contact.topic.required}"));
    }

    @Test
    void aContactInvitationNeverAsksForAThirdPartyNationalId() {
        List<String> fields = Arrays.stream(InviteContactToCompanyDto.class.getDeclaredFields())
                .map(Field::getName)
                .toList();

        assertFalse(fields.contains("tcIdentityNumber"),
                "Davet govdesi ucuncu kisinin kimlik numarasini tasiyamaz, alanlar: " + fields);
    }

    @Test
    void anAddressAcceptsStableReferenceIdsAndRejectsPartialSelection() {
        AddressWriteDto address = new AddressWriteDto();
        address.setCountryId(UUID.randomUUID());
        address.setComponents(components());
        address.setPosition(position());

        assertTrue(messagesFor(address).contains("{address.city.notNull}"));
        assertTrue(messagesFor(address).contains("{address.district.notNull}"));

        address.setCityId(UUID.randomUUID());
        assertTrue(messagesFor(address).contains("{address.district.notNull}"));

        address.setDistrictId(UUID.randomUUID());
        assertTrue(isValid(address));
    }

    @Test
    void anIstanbulAddressRequiresStructuredPostalComponents() {
        AddressWriteDto address = validAddress();
        address.setComponents(new AddressComponentsRequestDto());

        Set<String> messages = messagesFor(address);

        assertTrue(messages.contains("{address.neighborhood.notBlank}"));
        assertTrue(messages.contains("{address.thoroughfare.notBlank}"));
        assertTrue(messages.contains("{address.postalCode.invalid}"));
        assertTrue(messages.contains("{address.locationDetail.required}"));
    }

    @Test
    void aSiteNameCanReplaceABuildingNumberForNumberlessWorkplaces() {
        AddressWriteDto address = validAddress();
        address.getComponents().setBuildingNumber(null);
        address.getComponents().setSiteName("İstanbul Kongre Merkezi");

        assertTrue(isValid(address));
    }

    @Test
    void anAddressRejectsCoordinatesOutsideWorldBounds() {
        AddressWriteDto address = validAddress();
        address.getPosition().setLatitude(new BigDecimal("90.000001"));
        address.getPosition().setLongitude(new BigDecimal("-180.000001"));

        Set<String> messages = messagesFor(address);

        assertTrue(messages.contains("{address.latitude.range}"));
        assertTrue(messages.contains("{address.longitude.range}"));
    }

    private static AddressWriteDto validAddress() {
        AddressWriteDto address = new AddressWriteDto();
        address.setCountryId(UUID.randomUUID());
        address.setCityId(UUID.randomUUID());
        address.setDistrictId(UUID.randomUUID());
        address.setComponents(components());
        address.setPosition(position());
        return address;
    }

    private static AddressComponentsRequestDto components() {
        AddressComponentsRequestDto components = new AddressComponentsRequestDto();
        components.setNeighborhood("Caferağa Mahallesi");
        components.setThoroughfare("Moda Caddesi");
        components.setBuildingNumber("12");
        components.setPostalCode("34710");
        return components;
    }

    private static AddressPositionRequestDto position() {
        AddressPositionRequestDto position = new AddressPositionRequestDto();
        position.setLatitude(new BigDecimal("40.982512"));
        position.setLongitude(new BigDecimal("29.025411"));
        position.setSource(ConfirmedPositionSource.USER_CONFIRMED_PIN);
        return position;
    }

    @Test
    void aReportMustPointAtAtLeastOneTarget() {
        CreateReportDto empty = new CreateReportDto();
        empty.setReason(ReportReason.SPAM);

        assertTrue(messagesFor(empty).contains("{report.target.required}"));

        empty.setJobId(UUID.randomUUID());
        assertTrue(isValid(empty));

        empty.setMessageId(UUID.randomUUID());
        assertTrue(isValid(empty));
    }

    @Test
    void aReportCannotBeDecidedWithoutAReason() {
        ReviewReportDto blank = new ReviewReportDto();
        blank.setStatus(ReportStatus.ACTIONED);

        assertTrue(messagesFor(blank).contains("{report.review.note.required}"),
                "Karar gerekcesi sunucuda da zorunlu olmali, yalnizca panelde degil.");

        blank.setNote("   ");
        assertTrue(messagesFor(blank).contains("{report.review.note.required}"),
                "Yalnizca bosluktan olusan gerekce de bos sayilmali.");

        blank.setNote("Hesap askiya alindi.");
        assertTrue(isValid(blank));
    }

    @Test
    void anIdentityReviewCannotBeRejectedWithoutAReason() {
        RejectIdentityReviewDto blank = new RejectIdentityReviewDto();

        assertTrue(messagesFor(blank).contains(IdentityMessages.REJECTION_REASON_REQUIRED),
                "Red gerekcesi DTO'da zorunlu; manager ikinci bir null/blank kapisi tasiyamaz.");

        blank.setReason("   ");
        assertTrue(messagesFor(blank).contains(IdentityMessages.REJECTION_REASON_REQUIRED),
                "Yalnizca bosluktan olusan gerekce de bos sayilmali.");

        blank.setReason("Fotoğraflar okunaksız");
        assertTrue(isValid(blank));
    }

    @Test
    void aCountBadgeWithoutItsThresholdIsRejected() {
        SaveBadgeDto badge = new SaveBadgeDto();
        badge.setCode("YENI_ROZET");
        badge.setFamily(BadgeFamily.OTHER);
        badge.setTier(BadgeTier.BRONZE);
        badge.setName("Yeni");
        badge.setSortOrder(0);
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
        badge.setSortOrder(0);
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
        badge.setSortOrder(0);
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
        education.setGpa(3.2);
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
        education.setGpa(3.2);
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
    void aTransactionalNotificationCannotBeSwitchedOff() {
        UpdateNotificationPreferencesDto request = new UpdateNotificationPreferencesDto();
        request.setPreferences(List.of(
                entry(NotificationCategory.APPLICATION_UPDATES, NotificationChannel.EMAIL, false)));

        assertTrue(messagesFor(request).contains("{notification.preference.not.optional}"));
    }

    @Test
    void aTimesheetEventIdentityAndCapturedTimeTravelTogether() {
        CheckPointDto request = new CheckPointDto();
        request.setClientEventId(UUID.randomUUID());

        assertTrue(messagesFor(request).contains("{timesheet.client.event.pair}"));
        assertEquals(Set.of("eventIdentityComplete"), validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet()));

        request.setClientEventId(null);
        request.setCapturedAt(Instant.parse("2026-09-06T10:00:00Z"));
        assertTrue(messagesFor(request).contains("{timesheet.client.event.pair}"));

        request.setClientEventId(UUID.randomUUID());
        assertTrue(isValid(request));
    }

    @Test
    void aCompanyContactInvitationCannotBeSwitchedOff() {
        UpdateNotificationPreferencesDto request = new UpdateNotificationPreferencesDto();
        request.setPreferences(List.of(
                entry(NotificationCategory.COMPANY_UPDATES, NotificationChannel.EMAIL, false)));

        assertTrue(messagesFor(request).contains("{notification.preference.not.optional}"));
    }

    @Test
    void anOptionalNotificationCanBeSwitchedOffAndBackOn() {
        UpdateNotificationPreferencesDto off = new UpdateNotificationPreferencesDto();
        off.setPreferences(List.of(entry(NotificationCategory.JOB_ALERTS, NotificationChannel.EMAIL, false)));
        assertTrue(isValid(off));

        UpdateNotificationPreferencesDto on = new UpdateNotificationPreferencesDto();
        on.setPreferences(List.of(entry(NotificationCategory.MARKETINGS, NotificationChannel.EMAIL, true)));
        assertTrue(isValid(on));
    }

    @Test
    void smsCanBeSubmittedAsANotificationPreference() {
        UpdateNotificationPreferencesDto request = new UpdateNotificationPreferencesDto();
        request.setPreferences(List.of(entry(NotificationCategory.MARKETINGS, NotificationChannel.SMS, true)));

        assertTrue(isValid(request));
    }

    private NotificationPreferenceEntryDto entry(NotificationCategory category, NotificationChannel channel,
                                           boolean enabled) {
        NotificationPreferenceEntryDto entry = new NotificationPreferenceEntryDto();
        entry.setCategory(category);
        entry.setChannel(channel);
        entry.setEnabled(enabled);
        return entry;
    }

    @Test
    void everyCrossFieldRuleReportsAMessageKeyNotARawSentence() {
        SaveBadgeDto badge = new SaveBadgeDto();
        badge.setRuleType(BadgeRuleType.COMPLETED_ASSIGNMENTS);

        assertTrue(messagesFor(badge).stream().allMatch(m -> m.startsWith("{") && m.endsWith("}")),
                "doğrulama mesajları çözümlenebilir anahtar olmalı: " + messagesFor(badge));
    }
}
