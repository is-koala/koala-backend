package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.NotificationService;
import com.exskylab.koala.core.dtos.notification.request.SendEmailDto;
import com.exskylab.koala.core.dtos.notification.request.SendPushToUserDto;
import com.exskylab.koala.core.helpers.MessageResolver;
import com.exskylab.koala.entities.ApplicationStatus;
import com.exskylab.koala.entities.AssignmentStatus;
import com.exskylab.koala.entities.Cancellation;
import com.exskylab.koala.entities.Company;
import com.exskylab.koala.entities.CompanyApprovalStatus;
import com.exskylab.koala.entities.CompanyContact;
import com.exskylab.koala.entities.CompanyContactRole;
import com.exskylab.koala.entities.Dispute;
import com.exskylab.koala.entities.Job;
import com.exskylab.koala.entities.JobApplication;
import com.exskylab.koala.entities.JobAssignment;
import com.exskylab.koala.entities.NotificationCategory;
import com.exskylab.koala.entities.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

class ActivityNotificationRulesTest {

    private NotificationService notificationService;
    private ActivityNotificationManager manager;

    private final User worker = user();
    private final User employer = user();
    private Job job;

    @BeforeEach
    void setUp() {
        notificationService = mock(NotificationService.class);
        MessageResolver messageResolver = mock(MessageResolver.class);
        when(messageResolver.resolve(any())).thenAnswer(call -> "metin:" + call.getArgument(0));

        manager = new ActivityNotificationManager(notificationService, messageResolver,
                new com.exskylab.koala.core.properties.AppProperties("http://localhost:8080", "http://localhost:3000"));

        job = new Job();
        job.setId(UUID.randomUUID());
        job.setTitle("Barista aranıyor");
        job.setCreator(employer);
    }

    private static User user() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFirstName("Ayşe");
        user.setEmail("ayse@koala.test");
        return user;
    }

    private JobAssignment assignment() {
        JobAssignment assignment = new JobAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setJob(job);
        assignment.setWorker(worker);
        return assignment;
    }

    private SendPushToUserDto captured() {
        ArgumentCaptor<SendPushToUserDto> captor = ArgumentCaptor.forClass(SendPushToUserDto.class);
        verify(notificationService).sendPushToUser(captor.capture(), any(), anyBoolean());
        return captor.getValue();
    }

    private Company company(CompanyApprovalStatus status, User... admins) {
        Company company = new Company();
        company.setId(UUID.randomUUID());
        company.setName("Yalancı Kahve Ltd.");
        company.setApprovalStatus(status);

        for (User admin : admins) {
            CompanyContact contact = new CompanyContact();
            contact.setUser(admin);
            contact.setRole(CompanyContactRole.ADMIN);
            company.addContact(contact);
        }
        return company;
    }

    @Test
    void anApprovalDecisionReachesTheCompanyAdmin() {
        manager.companyApprovalDecided(company(CompanyApprovalStatus.APPROVED, employer));

        assertEquals(employer, captured().getRecipient());
        assertEquals("Yalancı Kahve Ltd.", captured().getTitle());
    }

    @Test
    void aCompanyDecisionCannotBeSilencedByAPreference() {
        manager.companyApprovalDecided(company(CompanyApprovalStatus.REJECTED, employer));

        assertEquals(NotificationCategory.COMPANY_UPDATES, captured().getCategory());
    }

    @Test
    void aCompanyDecisionOnlyReachesTheAdminsNotTheStaff() {
        Company company = company(CompanyApprovalStatus.APPROVED, employer);
        CompanyContact staff = new CompanyContact();
        staff.setUser(worker);
        staff.setRole(CompanyContactRole.STAFF);
        company.addContact(staff);

        manager.companyApprovalDecided(company);

        verify(notificationService, times(1)).sendPushToUser(any(), any(), anyBoolean());
        assertEquals(employer, captured().getRecipient());
    }

    @Test
    void aCompanyDecisionFeedEntryPointsAtTheCompany() {
        Company company = company(CompanyApprovalStatus.REJECTED, employer);

        manager.companyApprovalDecided(company);

        verify(notificationService).sendInApp(argThat(dto -> {
            assertEquals(com.exskylab.koala.entities.NotificationTargetType.COMPANY, dto.getTargetType());
            assertEquals(company.getId(), dto.getTargetId());
            return true;
        }));
    }

    @Test
    void approvalAndRejectionDoNotSayTheSameThing() {
        manager.companyApprovalDecided(company(CompanyApprovalStatus.APPROVED, employer));
        String approvalBody = captured().getBody();

        reset(notificationService);
        manager.companyApprovalDecided(company(CompanyApprovalStatus.REJECTED, employer));

        assertNotEquals(approvalBody, captured().getBody());
    }

    @Test
    void anAnsweredApplicationReachesTheApplicant() {
        JobApplication application = new JobApplication();
        application.setJob(job);
        application.setUser(worker);
        application.setStatus(ApplicationStatus.ACCEPTED);

        manager.applicationAnswered(application);

        assertEquals(worker, captured().getRecipient());
        assertEquals(NotificationCategory.APPLICATION_UPDATES, captured().getCategory());
    }

    @Test
    void anAnsweredAssignmentReachesTheEmployerNotTheWorker() {
        JobAssignment assignment = assignment();
        assignment.setStatus(AssignmentStatus.CONFIRMED);

        manager.assignmentAnswered(assignment);

        assertEquals(employer, captured().getRecipient());
    }

    @Test
    void aDisputeOpenedByTheWorkerNotifiesTheEmployer() {
        Dispute dispute = new Dispute();
        dispute.setJobAssignment(assignment());
        dispute.setOpenedBy(worker);

        manager.disputeOpened(dispute);

        assertEquals(employer, captured().getRecipient());
    }

    @Test
    void aCancellationByTheEmployerNotifiesTheWorker() {
        Cancellation cancellation = new Cancellation();
        cancellation.setJobAssignment(assignment());
        cancellation.setCancelledBy(employer);

        manager.assignmentCancelled(cancellation);

        assertEquals(worker, captured().getRecipient());
    }

    @Test
    void aResolvedDisputeReachesBothSides() {
        Dispute dispute = new Dispute();
        dispute.setJobAssignment(assignment());

        manager.disputeResolved(dispute);

        verify(notificationService, times(2)).sendPushToUser(any(), any(), anyBoolean());
    }

    @Test
    void identityVerificationUsesItsOwnCategory() {
        manager.identityVerified(worker);

        assertEquals(NotificationCategory.IDENTITY_VERIFICATION, captured().getCategory());
    }

    @Test
    void everyActivityGoesOutOnBothChannels() {
        manager.assignmentCompleted(assignment());

        verify(notificationService).sendPushToUser(any(), any(), anyBoolean());
        verify(notificationService).sendEmail(any(), any(), anyBoolean());
    }

    @Test
    void activityEmailActionUsesTheWebBaseUrl() {
        manager = new ActivityNotificationManager(notificationService, mock(MessageResolver.class),
                new com.exskylab.koala.core.properties.AppProperties(
                        "https://api.iskoala.com", "https://iskoala.com"));

        manager.assignmentCompleted(assignment());

        ArgumentCaptor<SendEmailDto> captor = ArgumentCaptor.forClass(SendEmailDto.class);
        verify(notificationService).sendEmail(captor.capture(), any(), anyBoolean());
        assertEquals("https://iskoala.com", captor.getValue().getTemplateParameters().get("actionUrl"));
    }

    @Test
    void aRecipientWithoutAnEmailStillGetsThePush() {
        JobAssignment assignment = assignment();
        assignment.getWorker().setEmail(null);

        manager.assignmentCompleted(assignment);

        verify(notificationService).sendPushToUser(any(), any(), anyBoolean());
        verify(notificationService, never()).sendEmail(any(), any(), anyBoolean());
    }

    @Test
    void aFailingPushDoesNotStopTheEmail() {
        doThrow(new IllegalStateException("push down"))
                .when(notificationService).sendPushToUser(any(), any(), anyBoolean());

        manager.assignmentCompleted(assignment());

        verify(notificationService).sendEmail(any(), any(), anyBoolean());
    }

    @Test
    void aDeliveryFailureNeverBreaksTheBusinessFlow() {
        doThrow(new IllegalStateException("push down"))
                .when(notificationService).sendPushToUser(any(), any(), anyBoolean());

        assertDoesNotThrow(() -> manager.assignmentCompleted(assignment()));
    }

    @Test
    void aMissingRecipientIsSkippedRatherThanThrowing() {
        JobAssignment orphan = assignment();
        orphan.getJob().setCreator(null);
        orphan.setStatus(AssignmentStatus.CONFIRMED);

        assertDoesNotThrow(() -> manager.assignmentAnswered(orphan));
        verifyNoInteractions(notificationService);
    }

    @Test
    void everyActivityAlsoLandsInTheInAppFeedExactlyOnce() {
        JobAssignment assignment = assignment();

        manager.assignmentCompleted(assignment);

        verify(notificationService).sendInApp(argThat(dto -> {
            assertEquals(assignment.getWorker(), dto.getRecipient());
            assertEquals(assignment.getJob().getTitle(), dto.getSubject());
            assertNotNull(dto.getBody());
            return true;
        }));
    }

    @Test
    void theFeedEntryKnowsWhereTappingShouldGo() {
        JobAssignment assignment = assignment();

        manager.assignmentCompleted(assignment);

        verify(notificationService).sendInApp(argThat(dto -> {
            assertEquals(com.exskylab.koala.entities.NotificationTargetType.JOB_ASSIGNMENT, dto.getTargetType());
            assertEquals(assignment.getId(), dto.getTargetId());
            return true;
        }));
    }

    @Test
    void aFeedFailureDoesNotStopTheOtherChannels() {
        doThrow(new RuntimeException("feed down")).when(notificationService).sendInApp(any());

        assertDoesNotThrow(() -> manager.assignmentCompleted(assignment()));

        verify(notificationService).sendEmail(any(), any(), anyBoolean());
    }
}
