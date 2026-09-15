package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.NotificationService;
import com.exskylab.koala.core.constants.ActivityMessages;
import com.exskylab.koala.core.dtos.notification.request.SendEmailDto;
import com.exskylab.koala.core.dtos.notification.request.SendPushToUserDto;
import com.exskylab.koala.core.helpers.MessageResolver;
import com.exskylab.koala.entities.Address;
import com.exskylab.koala.entities.ApplicationStatus;
import com.exskylab.koala.entities.AssignmentStatus;
import com.exskylab.koala.entities.Cancellation;
import com.exskylab.koala.entities.City;
import com.exskylab.koala.entities.Company;
import com.exskylab.koala.entities.CompanyApprovalStatus;
import com.exskylab.koala.entities.CompanyContact;
import com.exskylab.koala.entities.CompanyContactRole;
import com.exskylab.koala.entities.Dispute;
import com.exskylab.koala.entities.Job;
import com.exskylab.koala.entities.JobApplication;
import com.exskylab.koala.entities.JobAssignment;
import com.exskylab.koala.entities.Conversation;
import com.exskylab.koala.entities.ConversationKind;
import com.exskylab.koala.entities.District;
import com.exskylab.koala.entities.Message;
import com.exskylab.koala.entities.NotificationCategory;
import com.exskylab.koala.entities.NotificationTargetType;
import com.exskylab.koala.entities.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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

        InOrder order = inOrder(notificationService);
        order.verify(notificationService).preparePushRecipients(argThat((Collection<UUID> ids) ->
                ids.size() == 2 && ids.contains(worker.getId()) && ids.contains(employer.getId())));
        order.verify(notificationService, times(2)).sendPushToUser(any(), any(), anyBoolean());
        verify(notificationService, times(2)).sendPushToUser(any(), any(), anyBoolean());
    }

    @Test
    void identityVerificationUsesItsOwnCategory() {
        manager.identityVerified(worker);

        assertEquals(NotificationCategory.IDENTITY_VERIFICATION, captured().getCategory());
    }

    @Test
    void anIdentityRevealReachesTheWorkerInAppAndByPushButNotByEmail() {
        JobAssignment assignment = assignment();

        manager.identityRevealed(assignment);

        verify(notificationService).sendInApp(argThat(dto -> dto.getRecipient() == worker
                && dto.getCategory() == NotificationCategory.ACCOUNT_SECURITY
                && dto.getTargetType() == NotificationTargetType.JOB_ASSIGNMENT
                && assignment.getId().equals(dto.getTargetId())
                && "metin:{activity.identity.revealed}".equals(dto.getBody())));
        SendPushToUserDto push = captured();
        assertEquals(worker, push.getRecipient());
        assertEquals(NotificationCategory.ACCOUNT_SECURITY, push.getCategory());
        assertEquals("metin:{activity.identity.revealed}", push.getBody());
        verify(notificationService, never()).sendEmail(any(), any(), anyBoolean());
    }

    @Test
    void anIdentityRevealWithoutAWorkerIsSilentlyDropped() {
        JobAssignment assignment = assignment();
        assignment.setWorker(null);

        manager.identityRevealed(assignment);

        verify(notificationService, never()).sendInApp(any());
        verify(notificationService, never()).sendPushToUser(any(), any(), anyBoolean());
    }

    @Test
    void everyActivityGoesOutOnBothChannels() {
        manager.assignmentCompleted(assignment());

        verify(notificationService).sendPushToUser(any(), any(), anyBoolean());
        verify(notificationService).sendEmail(any(), any(), anyBoolean());
    }

    @Test
    void activityEmailCarriesWhenWhereWhoAmountAndADurableDeepLink() {
        JobAssignment assignment = completedWorkEmailFixture();

        manager.assignmentCompleted(assignment);

        Map<String, Object> params = capturedEmail().getTemplateParameters();
        assertEquals("20 Eylül 2026, 09:30", params.get("when"));
        assertEquals("Kadıköy, İstanbul", params.get("where"));
        assertEquals("Ayşe Yılmaz", params.get("who"));
        assertEquals("1.250,00 ₺", params.get("amount"));
        assertEquals("http://localhost:3000/panel/is/" + assignment.getId(), params.get("actionUrl"));
        assertFalse(params.get("actionUrl").toString().equals("http://localhost:3000"));
        assertEquals("Barista aranıyor", params.get("jobTitle"));
        assertEquals("metin:" + ActivityMessages.WORK_COMPLETED, params.get("message"));
    }

    @Test
    void activityPushAndFeedKeepTheOneLinerWhenTheEmailAddsJobFacts() {
        JobAssignment assignment = completedWorkEmailFixture();

        manager.assignmentCompleted(assignment);

        SendPushToUserDto push = captured();
        assertEquals("metin:" + ActivityMessages.WORK_COMPLETED, push.getBody());
        assertFalse(push.getBody().contains("Kadıköy"));
        assertFalse(push.getBody().contains("1.250"));

        verify(notificationService).sendInApp(argThat(dto -> {
            assertEquals("metin:" + ActivityMessages.WORK_COMPLETED, dto.getBody());
            assertFalse(dto.getBody().contains("Kadıköy"));
            return true;
        }));
    }

    @Test
    void activityEmailActionUsesTheWebBaseUrl() {
        MessageResolver resolver = mock(MessageResolver.class);
        when(resolver.resolve(any())).thenAnswer(call -> "metin:" + call.getArgument(0));
        manager = new ActivityNotificationManager(notificationService, resolver,
                new com.exskylab.koala.core.properties.AppProperties(
                        "https://api.iskoala.com", "https://iskoala.com"));

        JobAssignment assignment = assignment();

        manager.assignmentCompleted(assignment);

        String actionUrl = capturedEmail().getTemplateParameters().get("actionUrl").toString();
        assertEquals("https://iskoala.com/panel/is/" + assignment.getId(), actionUrl);
        assertFalse(actionUrl.equals("https://iskoala.com"));
        assertFalse(actionUrl.equals("https://iskoala.com/"));
    }

    @Test
    void identityVerifiedEmailOpensTheProfileNotTheSiteRoot() {
        manager.identityVerified(worker);

        String actionUrl = capturedEmail().getTemplateParameters().get("actionUrl").toString();
        assertEquals("http://localhost:3000/panel/profil", actionUrl);
        assertFalse(actionUrl.equals("http://localhost:3000"));
    }

    @Test
    void assignmentAnsweredEmailOpensTheEmployerAssignmentPage() {
        JobAssignment assignment = assignment();
        assignment.setStatus(AssignmentStatus.CONFIRMED);

        manager.assignmentAnswered(assignment);

        String actionUrl = capturedEmail().getTemplateParameters().get("actionUrl").toString();
        assertEquals("http://localhost:3000/isveren/atama/" + assignment.getId(), actionUrl);
        assertFalse(actionUrl.equals("http://localhost:3000"));
    }

    private JobAssignment completedWorkEmailFixture() {
        employer.setLastName("Yılmaz");
        City city = new City();
        city.setName("İstanbul");
        District district = new District();
        district.setName("Kadıköy");
        Address address = new Address();
        address.setCity(city);
        address.setDistrict(district);
        job.setAddress(address);
        job.setStartDateTime(LocalDateTime.of(2026, 9, 20, 9, 30));
        job.setCurrency("TRY");

        JobAssignment assignment = assignment();
        assignment.setWorkerPayout(new BigDecimal("1250.00"));
        assignment.setCurrency("TRY");
        return assignment;
    }

    private SendEmailDto capturedEmail() {
        ArgumentCaptor<SendEmailDto> captor = ArgumentCaptor.forClass(SendEmailDto.class);
        verify(notificationService).sendEmail(captor.capture(), any(), anyBoolean());
        return captor.getValue();
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

    private Message message(User sender, ConversationKind kind, User... participants) {
        Conversation conversation = new Conversation();
        conversation.setId(UUID.randomUUID());
        conversation.setKind(kind);
        conversation.setJob(job);
        conversation.setEmployer(employer);
        for (User participant : participants) {
            conversation.addParticipant(participant);
        }
        Message message = new Message();
        message.setId(UUID.randomUUID());
        message.setConversation(conversation);
        message.setSender(sender);
        message.setBody("Yarın saat kaçta?");
        return message;
    }

    private List<SendPushToUserDto> capturedPushes() {
        ArgumentCaptor<SendPushToUserDto> captor = ArgumentCaptor.forClass(SendPushToUserDto.class);
        verify(notificationService, atLeast(0)).sendPushToUser(captor.capture(), any(), anyBoolean());
        return captor.getAllValues();
    }

    @Test
    void aDirectMessageIsPushedToTheCounterpartOnly() {
        User sender = worker;
        sender.setLastName("Yılmaz");

        manager.messageSent(message(sender, ConversationKind.DIRECT, employer, sender));

        List<SendPushToUserDto> pushes = capturedPushes();
        assertEquals(1, pushes.size());
        assertEquals(employer, pushes.get(0).getRecipient());
        assertEquals(NotificationCategory.MESSAGES, pushes.get(0).getCategory());
        assertEquals("Ayşe Yılmaz", pushes.get(0).getTitle());
        assertEquals("metin:" + ActivityMessages.MESSAGE_RECEIVED, pushes.get(0).getBody());
    }

    @Test
    void theMessageTextNeverEntersThePush() {
        Message message = message(worker, ConversationKind.DIRECT, employer, worker);

        manager.messageSent(message);

        SendPushToUserDto push = captured();
        assertFalse(push.getBody().contains(message.getBody()));
        assertFalse(push.getTitle().contains(message.getBody()));
        assertEquals(Map.of("type", "MESSAGE",
                "conversationId", message.getConversation().getId().toString(),
                "messageId", message.getId().toString()), push.getDataPayload());
    }

    @Test
    void aGroupMessageReachesEveryOtherParticipant() {
        User second = user();
        User third = user();

        manager.messageSent(message(employer, ConversationKind.GROUP, employer, worker, second, third));

        List<SendPushToUserDto> pushes = capturedPushes();
        assertEquals(3, pushes.size());
        assertTrue(pushes.stream().noneMatch(push -> push.getRecipient().equals(employer)));
        verify(notificationService).preparePushRecipients(argThat((Collection<UUID> ids) ->
                ids.size() == 3 && !ids.contains(employer.getId())));
    }

    @Test
    void aMessagePushIsNeitherAFeedEntryNorAnEmail() {
        manager.messageSent(message(worker, ConversationKind.DIRECT, employer, worker));

        verify(notificationService, never()).sendInApp(any());
        verify(notificationService, never()).sendEmail(any(), any(), anyBoolean());
    }

    @Test
    void aMessagePushCarriesTheHumanSenderNotTheSystem() {
        manager.messageSent(message(worker, ConversationKind.DIRECT, employer, worker));

        verify(notificationService).sendPushToUser(any(), any(), eq(false));
    }

    @Test
    void aSenderWithoutANameStillGetsATitle() {
        User anonymous = new User();
        anonymous.setId(UUID.randomUUID());

        manager.messageSent(message(anonymous, ConversationKind.DIRECT, employer, anonymous));

        assertEquals("metin:" + ActivityMessages.MESSAGE_RECEIVED_TITLE, captured().getTitle());
    }
}
