package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.StaffAuditService;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.core.constants.LegalMessages;
import com.exskylab.koala.core.dtos.legal.request.ConsentDecisionDto;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.helpers.ClientContext;
import com.exskylab.koala.business.abstracts.DisputeService;
import com.exskylab.koala.business.abstracts.PaymentService;
import com.exskylab.koala.core.privacy.AccountAnonymizer;
import com.exskylab.koala.dataAccess.AccountDeletionRequestDao;
import com.exskylab.koala.dataAccess.AgreementVersionDao;
import com.exskylab.koala.dataAccess.ConsentRecordDao;
import com.exskylab.koala.dataAccess.JobAssignmentDao;
import com.exskylab.koala.entities.*;
import com.exskylab.koala.core.exceptions.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LegalConsentTest {

    private AgreementVersionDao agreementVersionDao;
    private ConsentRecordDao consentRecordDao;
    private AccountDeletionRequestDao deletionDao;
    private com.exskylab.koala.business.abstracts.JobAssignmentService jobAssignmentService;
    private StaffAuditService staffAuditService;
    private DisputeService disputeService;
    private PaymentService paymentService;
    private AccountAnonymizer accountAnonymizer;
    private LegalManager legalManager;

    private AgreementVersion terms;
    private AgreementVersion privacy;
    private AgreementVersion marketing;
    private User user;

    @BeforeEach
    void setUp() {
        agreementVersionDao = mock(AgreementVersionDao.class);
        consentRecordDao = mock(ConsentRecordDao.class);
        deletionDao = mock(AccountDeletionRequestDao.class);
        jobAssignmentService = mock(com.exskylab.koala.business.abstracts.JobAssignmentService.class);
        staffAuditService = mock(StaffAuditService.class);
        disputeService = mock(DisputeService.class);
        paymentService = mock(PaymentService.class);
        accountAnonymizer = mock(AccountAnonymizer.class);

        legalManager = new LegalManager(agreementVersionDao, consentRecordDao, deletionDao,
                jobAssignmentService, disputeService, paymentService, accountAnonymizer, staffAuditService);

        terms = agreement(AgreementType.TERMS_OF_SERVICE, true);
        privacy = agreement(AgreementType.PRIVACY_POLICY, true);
        marketing = agreement(AgreementType.COMMERCIAL_MESSAGE, false);

        user = new User();
        user.setId(UUID.randomUUID());

        when(agreementVersionDao.findAllCurrent()).thenReturn(List.of(terms, privacy, marketing));
        when(consentRecordDao.saveAll(any())).thenAnswer(i -> i.getArgument(0));
        when(deletionDao.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private AgreementVersion agreement(AgreementType type, boolean mandatory) {
        AgreementVersion version = new AgreementVersion();
        version.setId(UUID.randomUUID());
        version.setType(type);
        version.setVersion("1.0");
        version.setLocale("tr");
        version.setMandatory(mandatory);
        version.setEffectiveFrom(Instant.now());
        return version;
    }

    private ConsentDecisionDto decision(AgreementVersion version, boolean granted) {
        return new ConsentDecisionDto(version.getId(), granted);
    }

    @Test
    void registrationIsBlockedWhenAMandatoryAgreementIsNotAccepted() {
        assertThrows(ValidationException.class, () -> legalManager.assertMandatoryConsentsGranted(
                List.of(decision(terms, true))));

        assertThrows(ValidationException.class, () -> legalManager.assertMandatoryConsentsGranted(List.of()));
        assertThrows(ValidationException.class, () -> legalManager.assertMandatoryConsentsGranted(null));
    }

    @Test
    void explicitlyRefusingAMandatoryAgreementIsNotEnough() {
        assertThrows(ValidationException.class, () -> legalManager.assertMandatoryConsentsGranted(
                List.of(decision(terms, true), decision(privacy, false))));
    }

    @Test
    void acceptingEveryMandatoryAgreementPasses() {
        assertDoesNotThrow(() -> legalManager.assertMandatoryConsentsGranted(
                List.of(decision(terms, true), decision(privacy, true))));
    }

    @Test
    void anOptionalAgreementCanBeRefusedWithoutBlockingRegistration() {
        assertDoesNotThrow(() -> legalManager.assertMandatoryConsentsGranted(
                List.of(decision(terms, true), decision(privacy, true), decision(marketing, false))));
    }

    @Test
    void everyDecisionIsStoredWithWhoWhenAndFromWhere() {
        when(agreementVersionDao.findAllById(any())).thenReturn(List.of(terms, marketing));

        List<ConsentRecord> records = legalManager.recordConsents(user,
                List.of(decision(terms, true), decision(marketing, false)),
                new ClientContext("88.244.1.5", "Koala/1.0.0 (android)", "device-1"));

        assertEquals(2, records.size());
        ConsentRecord accepted = records.stream().filter(ConsentRecord::isGranted).findFirst().orElseThrow();
        assertEquals(user, accepted.getUser());
        assertEquals("88.244.1.5", accepted.getIpAddress());
        assertEquals("Koala/1.0.0 (android)", accepted.getUserAgent());
        assertNotNull(accepted.getRecordedAt());

        ConsentRecord refused = records.stream().filter(r -> !r.isGranted()).findFirst().orElseThrow();
        assertEquals(AgreementType.COMMERCIAL_MESSAGE, refused.getAgreementVersion().getType());
    }

    @Test
    void aDecisionPointingAtAnUnknownAgreementIsRejected() {
        when(agreementVersionDao.findAllById(any())).thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class, () -> legalManager.recordConsents(user,
                List.of(decision(terms, true)), null));
    }

    @Test
    void deletionRequestRemainsVisibleWhileMoneyIsStillInEscrow() {
        when(jobAssignmentService.hasMoneyInEscrowFor(any())).thenReturn(true);

        AccountDeletionRequest request = legalManager.requestAccountDeletion(user, "istemiyorum");

        assertEquals(AccountDeletionStatus.REQUESTED, request.getStatus());
        assertEquals(LegalMessages.DELETION_BLOCKED_BY_ESCROW, request.getBlockedReason());
        verify(deletionDao).save(request);
    }

    @Test
    void deletionRequestRemainsVisibleWhileWorkIsStillOngoing() {
        when(jobAssignmentService.hasActiveWorkFor(any())).thenReturn(true);

        AccountDeletionRequest request = legalManager.requestAccountDeletion(user, null);

        assertEquals(AccountDeletionStatus.REQUESTED, request.getStatus());
        assertEquals(LegalMessages.DELETION_BLOCKED_BY_ACTIVE_WORK, request.getBlockedReason());
    }

    @Test
    void deletionRequestRemainsVisibleWhileADisputeIsActive() {
        when(disputeService.hasActiveDisputeForUser(user.getId())).thenReturn(true);

        AccountDeletionRequest request = legalManager.requestAccountDeletion(user, null);

        assertEquals(LegalMessages.DELETION_BLOCKED_BY_DISPUTE, request.getBlockedReason());
    }

    @Test
    void deletionRequestRemainsVisibleWhileAPaymentIsPending() {
        when(paymentService.hasPendingPaymentForUser(user.getId())).thenReturn(true);

        AccountDeletionRequest request = legalManager.requestAccountDeletion(user, null);

        assertEquals(LegalMessages.DELETION_BLOCKED_BY_PENDING_PAYMENT, request.getBlockedReason());
    }

    @Test
    void aSecondPendingRequestIsRejected() {
        when(deletionDao.findFirstByUserIdAndStatusOrderByRequestedAtDesc(
                user.getId(), AccountDeletionStatus.REQUESTED))
                .thenReturn(Optional.of(new AccountDeletionRequest()));

        assertThrows(ConflictException.class, () -> legalManager.requestAccountDeletion(user, null));
    }

    @Test
    void aCleanAccountCanBeQueuedForDeletion() {
        AccountDeletionRequest request = legalManager.requestAccountDeletion(user, "artik kullanmiyorum");

        assertEquals(AccountDeletionStatus.REQUESTED, request.getStatus());
        assertEquals("artik kullanmiyorum", request.getReason());
        assertNotNull(request.getRequestedAt());
    }

    @Test
    void anErasureRequestDropsTheAuditDetailAboutThatPerson() {
        legalManager.requestAccountDeletion(user, "kvkk");

        verify(staffAuditService).eraseDetailsForResource(user.getId().toString());
    }

    @Test
    void aQueuedButBlockedErasureRequestStillDropsTheAuditDetail() {
        when(jobAssignmentService.hasMoneyInEscrowFor(user.getId())).thenReturn(true);

        AccountDeletionRequest request = legalManager.requestAccountDeletion(user, "kvkk");

        assertEquals(LegalMessages.DELETION_BLOCKED_BY_ESCROW, request.getBlockedReason());
        verify(staffAuditService).eraseDetailsForResource(user.getId().toString());
    }

    @Test
    void aDueCleanRequestIsAnonymizedOnce() {
        AccountDeletionRequest request = deletionRequest();
        when(deletionDao.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));

        assertTrue(legalManager.processDeletionRequest(request.getId()));
        assertEquals(AccountDeletionStatus.ANONYMIZED, request.getStatus());
        assertNotNull(request.getProcessedAt());
        assertNull(request.getReason());
        verify(accountAnonymizer).anonymize(user.getId(), request.getId());

        assertFalse(legalManager.processDeletionRequest(request.getId()));
        verifyNoMoreInteractions(accountAnonymizer);
    }

    @Test
    void aStillBlockedRequestStaysQueuedWithAVisibleReason() {
        AccountDeletionRequest request = deletionRequest();
        when(deletionDao.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        when(disputeService.hasActiveDisputeForUser(user.getId())).thenReturn(true);

        assertFalse(legalManager.processDeletionRequest(request.getId()));
        assertEquals(AccountDeletionStatus.REQUESTED, request.getStatus());
        assertEquals(LegalMessages.DELETION_BLOCKED_BY_DISPUTE, request.getBlockedReason());
        assertTrue(request.getNextAttemptAt().isAfter(Instant.now()));
        verifyNoInteractions(accountAnonymizer);
    }

    @Test
    void anAdministratorCanRejectAQueuedRequestWithAReason() {
        AccountDeletionRequest request = deletionRequest();
        when(deletionDao.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));

        legalManager.rejectDeletionRequest(null, request.getId(), "Yasal saklama incelemesi sürüyor.");

        assertEquals(AccountDeletionStatus.REJECTED, request.getStatus());
        assertEquals("Yasal saklama incelemesi sürüyor.", request.getRejectionReason());
        assertNull(request.getBlockedReason());
        assertNull(request.getRejectedByStaffId());
        assertNotNull(request.getProcessedAt());
    }

    private AccountDeletionRequest deletionRequest() {
        AccountDeletionRequest request = new AccountDeletionRequest();
        request.setId(UUID.randomUUID());
        request.setUser(user);
        request.setSubjectId(user.getId());
        request.setStatus(AccountDeletionStatus.REQUESTED);
        request.setReason("kullanıcı gerekçesi");
        request.setRequestedAt(Instant.now().minusSeconds(86400));
        return request;
    }
}
