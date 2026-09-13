package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.ActivityNotificationService;
import com.exskylab.koala.business.abstracts.JobApplicationService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.dtos.common.PageDto;
import com.exskylab.koala.core.dtos.jobApplication.response.MyApplicationDto;
import com.exskylab.koala.dataAccess.JobApplicationDao;
import com.exskylab.koala.entities.ApplicationStatus;
import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class ApplicationWithdrawalIT extends AbstractIntegrationTest {

    @Autowired
    private JobApplicationService jobApplicationService;

    @Autowired
    private JobApplicationDao jobApplicationDao;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private SecurityService securityService;

    @MockitoBean
    private ActivityNotificationService activityNotificationService;

    private UUID applicantId;
    private UUID employerId;
    private UUID jobId;
    private UUID applicationId;

    @BeforeEach
    void seed() {
        applicantId = UUID.randomUUID();
        employerId = UUID.randomUUID();
        jobId = UUID.randomUUID();
        applicationId = UUID.randomUUID();

        insertUser(applicantId, "withdraw-applicant-" + applicantId + "@koala.test");
        insertUser(employerId, "withdraw-employer-" + employerId + "@koala.test");

        jdbcTemplate.update("""
                insert into jobs (id, created_at, creator_id, title, status, employer_type,
                                  experience_required, insurance_required, min_age, max_age)
                values (?, now(), ?, 'Barista aranıyor', 'OPEN', 'INDIVIDUAL', false, false, 18, 65)
                """, jobId, employerId);

        jdbcTemplate.update("""
                insert into job_applications
                    (id, created_at, job_id, user_id, status, application_date_time)
                values (?, now(), ?, ?, 'APPLIED', now())
                """, applicationId, jobId, applicantId);

        when(securityService.getAuthenticatedUserId()).thenReturn(applicantId);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from job_applications where job_id = ?", jobId);
        jdbcTemplate.update("delete from jobs where id = ?", jobId);
        jdbcTemplate.update("delete from users where id in (?, ?)", applicantId, employerId);
    }

    private void insertUser(UUID id, String email) {
        jdbcTemplate.update("""
                insert into users (id, created_at, email, first_name, last_name,
                                   email_verified, phone_verified, identity_verified, is_employer)
                values (?, now(), ?, 'Test', 'Kullanici', true, true, true, false)
                """, id, email);
    }

    private String statusInDatabase() {
        return jdbcTemplate.queryForObject(
                "select status from job_applications where id = ?", String.class, applicationId);
    }

    @Test
    void withdrawingChangesTheStatusWithoutTouchingTheSoftDeleteFlag() {
        jobApplicationService.withdrawApplication(applicationId);

        Integer rows = jdbcTemplate.queryForObject(
                "select count(*) from job_applications where id = ?", Integer.class, applicationId);
        assertEquals(1, rows, "Geri çekme kaydı tablodan silmemeliydi.");

        assertEquals("WITHDRAWN", statusInDatabase());

        Boolean deleted = jdbcTemplate.queryForObject(
                "select is_deleted from job_applications where id = ?", Boolean.class, applicationId);
        assertFalse(Boolean.TRUE.equals(deleted), "Geri çekme soft delete değildir, is_deleted false kalmalı.");

        assertTrue(jobApplicationDao.findById(applicationId).isPresent(),
                "Geri çekilmiş başvuru normal sorgularda görünmeye devam etmeli.");
    }

    @Test
    void aWithdrawnApplicationDisappearsFromTheAppliedFilter() {
        jobApplicationService.withdrawApplication(applicationId);

        PageDto<MyApplicationDto> applied =
                jobApplicationService.getMyApplications(ApplicationStatus.APPLIED, PageRequest.of(0, 20));
        assertTrue(applied.content().stream().noneMatch(row -> row.id().equals(applicationId)),
                "APPLIED süzgeci geri çekilmiş başvuruyu döndürmemeli.");

        PageDto<MyApplicationDto> withdrawn =
                jobApplicationService.getMyApplications(ApplicationStatus.WITHDRAWN, PageRequest.of(0, 20));
        assertTrue(withdrawn.content().stream().anyMatch(row -> row.id().equals(applicationId)),
                "WITHDRAWN süzgeci geri çekilmiş başvuruyu döndürmeli.");
    }

    @Test
    void theEmployerStillSeesTheWithdrawnApplicationInTheApplicantList() {
        jobApplicationService.withdrawApplication(applicationId);

        when(securityService.getAuthenticatedUserId()).thenReturn(employerId);

        assertTrue(jobApplicationDao.findAllByJobId(jobId).stream()
                        .anyMatch(application -> application.getId().equals(applicationId)
                                && application.getStatus() == ApplicationStatus.WITHDRAWN),
                "İşverenin başvuru listesi geri çekilmiş başvuruyu WITHDRAWN olarak taşımalı.");
    }

    @Test
    void theDuplicateGuardIgnoresWithdrawnApplicationsButNotLiveOnes() {
        assertTrue(jobApplicationDao.existsByJobIdAndUserIdAndStatusNot(
                        jobId, applicantId, ApplicationStatus.WITHDRAWN),
                "APPLIED başvuru yeniden başvuruyu engellemeli.");

        jobApplicationService.withdrawApplication(applicationId);

        assertFalse(jobApplicationDao.existsByJobIdAndUserIdAndStatusNot(
                        jobId, applicantId, ApplicationStatus.WITHDRAWN),
                "Geri çekilmiş başvuru yeniden başvuruyu engellememeli.");
        assertTrue(jobApplicationDao.existsById(applicationId),
                "Kayıt hâlâ duruyor; geçmiş korunmalı.");
    }
}
