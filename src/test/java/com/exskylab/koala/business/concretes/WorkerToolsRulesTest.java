package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.CompanyContactService;
import com.exskylab.koala.business.abstracts.CompanyService;
import com.exskylab.koala.business.abstracts.NotificationService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.dtos.availability.request.SaveAvailabilityDto;
import com.exskylab.koala.core.dtos.availability.request.SaveAvailabilityExceptionDto;
import com.exskylab.koala.core.dtos.experience.request.SaveExperienceDto;
import com.exskylab.koala.core.dtos.jobAlert.request.SaveJobAlertDto;
import com.exskylab.koala.core.dtos.jobTemplate.request.CreateJobTemplateDto;
import com.exskylab.koala.core.dtos.talent.request.SaveTalentPoolEntryDto;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.properties.JobAlertProperties;
import com.exskylab.koala.dataAccess.*;
import com.exskylab.koala.entities.*;
import com.exskylab.koala.core.exceptions.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import com.exskylab.koala.business.abstracts.JobService;
import com.exskylab.koala.business.abstracts.UserService;
import com.exskylab.koala.business.abstracts.JobCategoryService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WorkerToolsRulesTest {

    private ExperienceDao experienceDao;
    private AvailabilitySlotDao slotDao;
    private AvailabilityExceptionDao exceptionDao;
    private TalentPoolEntryDao talentPoolEntryDao;
    private JobTemplateDao jobTemplateDao;
    private JobAlertDao jobAlertDao;
    private JobService jobService;
    private SecurityService securityService;
    private JobAlertProperties alertProperties;

    private ExperienceManager experienceManager;
    private AvailabilityManager availabilityManager;
    private TalentPoolManager talentPoolManager;
    private JobTemplateManager jobTemplateManager;
    private JobAlertManager jobAlertManager;

    private final User me = user();
    private final User someoneElse = user();

    @BeforeEach
    void setUp() {
        experienceDao = mock(ExperienceDao.class);
        slotDao = mock(AvailabilitySlotDao.class);
        exceptionDao = mock(AvailabilityExceptionDao.class);
        talentPoolEntryDao = mock(TalentPoolEntryDao.class);
        jobTemplateDao = mock(JobTemplateDao.class);
        jobAlertDao = mock(JobAlertDao.class);
        jobService = mock(JobService.class);
        securityService = mock(SecurityService.class);
        alertProperties = new JobAlertProperties();

        UserService userService = mock(UserService.class);
        CompanyContactService contacts = mock(CompanyContactService.class);

        experienceManager = new ExperienceManager(experienceDao, securityService);
        availabilityManager = new AvailabilityManager(slotDao, exceptionDao, securityService);
        talentPoolManager = new TalentPoolManager(talentPoolEntryDao, userService,
                mock(CompanyService.class), securityService,
                new com.exskylab.koala.core.helpers.PageableSanitizer());
        jobTemplateManager = new JobTemplateManager(jobTemplateDao, jobService, contacts, securityService);
        jobAlertManager = new JobAlertManager(jobAlertDao, mock(JobCategoryService.class),
                mock(NotificationService.class), securityService, alertProperties);

        when(securityService.getAuthenticatedUser()).thenReturn(me);
        when(securityService.getAuthenticatedUserId()).thenReturn(me.getId());
        for (Object dao : List.of(experienceDao, slotDao, exceptionDao, talentPoolEntryDao,
                jobTemplateDao, jobAlertDao)) {
            when(((org.springframework.data.jpa.repository.JpaRepository<?, ?>) dao).save(any()))
                    .thenAnswer(i -> i.getArgument(0));
        }
        when(userService.getById(any())).thenAnswer(i -> {
            User u = new User();
            u.setId(i.getArgument(0));
            return u;
        });
        when(slotDao.findAllByUserIdOrderByDayOfWeekAscStartTimeAsc(any())).thenReturn(List.of());
        when(jobAlertDao.findAllByUserIdOrderByCreatedAtDesc(any())).thenReturn(List.of());
    }

    private static User user() {
        User u = new User();
        u.setId(UUID.randomUUID());
        return u;
    }

    private SaveExperienceDto experience(LocalDate start, LocalDate end) {
        SaveExperienceDto dto = new SaveExperienceDto();
        dto.setTitle("Barista");
        dto.setStartDate(start);
        dto.setEndDate(end);
        return dto;
    }

    private SaveAvailabilityDto.Slot slot(DayOfWeek day, String from, String to) {
        return new SaveAvailabilityDto.Slot(day, LocalTime.parse(from), LocalTime.parse(to));
    }

    @Test
    void anExperienceIsSavedAgainstTheSignedInUser() {
        Experience saved = experienceManager.addExperience(
                experience(LocalDate.of(2020, 1, 1), LocalDate.of(2021, 1, 1)));

        assertEquals(me.getId(), saved.getUser().getId());
        assertEquals("Barista", saved.getTitle());
    }

    @Test
    void anOngoingExperienceHasNoEndDate() {
        assertNull(experienceManager.addExperience(experience(LocalDate.of(2020, 1, 1), null)).getEndDate());
    }

    @Test
    void nobodyCanEditSomeoneElsesExperience() {
        Experience theirs = new Experience();
        theirs.setId(UUID.randomUUID());
        theirs.setUser(someoneElse);
        when(experienceDao.findWithUserById(theirs.getId())).thenReturn(Optional.of(theirs));

        assertThrows(ResourceNotFoundException.class,
                () -> experienceManager.deleteExperience(theirs.getId()));
        verify(experienceDao, never()).delete(any());
    }

    @Test
    void availabilityIsReplacedWholesaleNotAppended() {
        SaveAvailabilityDto request = new SaveAvailabilityDto(
                List.of(slot(DayOfWeek.MONDAY, "09:00", "17:00")));

        availabilityManager.replaceMySlots(request);

        verify(slotDao).deleteAll(any());
        verify(slotDao, times(1)).save(any());
    }

    @Test
    void overlappingSlotsOnTheSameDayAreRejected() {
        SaveAvailabilityDto request = new SaveAvailabilityDto(List.of(
                slot(DayOfWeek.MONDAY, "09:00", "13:00"),
                slot(DayOfWeek.MONDAY, "12:00", "17:00")));

        assertThrows(ValidationException.class, () -> availabilityManager.replaceMySlots(request));
    }

    @Test
    void theSameHoursOnDifferentDaysDoNotClash() {
        SaveAvailabilityDto request = new SaveAvailabilityDto(List.of(
                slot(DayOfWeek.MONDAY, "09:00", "17:00"),
                slot(DayOfWeek.TUESDAY, "09:00", "17:00")));

        assertDoesNotThrow(() -> availabilityManager.replaceMySlots(request));
    }

    @Test
    void savingTheSameDayTwiceUpdatesTheSameException() {
        LocalDate date = LocalDate.now().plusDays(3);
        AvailabilityException existing = new AvailabilityException();
        existing.setUser(me);
        existing.setDate(date);
        existing.setAvailable(false);
        when(exceptionDao.findByUserIdAndDate(me.getId(), date)).thenReturn(Optional.of(existing));

        availabilityManager.saveMyException(new SaveAvailabilityExceptionDto(date, true, "müsaitim"));

        assertTrue(existing.isAvailable());
        assertEquals("müsaitim", existing.getNote());
    }

    @Test
    void anEmployerCannotAddThemselvesToTheirOwnTalentPool() {
        assertThrows(ValidationException.class,
                () -> talentPoolManager.addWorker(me.getId(), new SaveTalentPoolEntryDto()));
    }

    @Test
    void addingTheSameWorkerTwiceUpdatesTheSameEntry() {
        UUID workerId = UUID.randomUUID();
        TalentPoolEntry existing = new TalentPoolEntry();
        existing.setEmployer(me);
        when(talentPoolEntryDao.findByEmployerIdAndWorkerId(me.getId(), workerId))
                .thenReturn(Optional.of(existing));

        talentPoolManager.addWorker(workerId, new SaveTalentPoolEntryDto(null, "iyi çalıştı"));

        assertEquals("iyi çalıştı", existing.getNote());
        verify(talentPoolEntryDao, times(1)).save(any());
    }

    @Test
    void removingAWorkerWhoWasNeverAddedIsHarmless() {
        when(talentPoolEntryDao.findByEmployerIdAndWorkerId(any(), any())).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> talentPoolManager.removeWorker(UUID.randomUUID()));
        verify(talentPoolEntryDao, never()).delete(any());
    }

    @Test
    void aTemplateCopiesTheJobItWasMadeFrom() {
        Job job = new Job();
        job.setId(UUID.randomUUID());
        job.setCreator(me);
        job.setTitle("Hafta sonu barista");
        job.setSalary(new BigDecimal("1500.00"));
        job.setInsuranceRequired(true);
        when(jobService.getById(job.getId())).thenReturn(job);

        JobTemplate template = jobTemplateManager.createFromJob(
                new CreateJobTemplateDto("Hafta sonu", job.getId()));

        assertEquals("Hafta sonu", template.getTemplateName());
        assertEquals("Hafta sonu barista", template.getTitle());
        assertEquals(new BigDecimal("1500.00"), template.getSalary());
        assertEquals(Boolean.TRUE, template.getInsuranceRequired());
    }

    @Test
    void aTemplateCannotBeMadeFromSomeoneElsesJob() {
        Job job = new Job();
        job.setId(UUID.randomUUID());
        job.setCreator(someoneElse);
        when(jobService.getById(job.getId())).thenReturn(job);

        assertThrows(ResourceNotFoundException.class,
                () -> jobTemplateManager.createFromJob(new CreateJobTemplateDto("Kopya", job.getId())));
    }

    @Test
    void theNumberOfAlertsPerUserIsCapped() {
        alertProperties.setMaxPerUser(1);
        when(jobAlertDao.findAllByUserIdOrderByCreatedAtDesc(me.getId()))
                .thenReturn(List.of(new JobAlert()));

        SaveJobAlertDto request = new SaveJobAlertDto();
        request.setAlertName("İkinci");
        request.setType(JobType.values()[0]);

        assertThrows(ValidationException.class, () -> jobAlertManager.createAlert(request));
    }
}
