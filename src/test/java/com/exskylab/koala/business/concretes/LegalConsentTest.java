package com.exskylab.koala.business.concretes;

import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.core.dtos.legal.request.ConsentDecisionDto;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.helpers.ClientContext;
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

        legalManager = new LegalManager(agreementVersionDao, consentRecordDao, deletionDao, jobAssignmentService);

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
    void deletionIsBlockedWhileMoneyIsStillInEscrow() {
        when(jobAssignmentService.hasMoneyInEscrowFor(any())).thenReturn(true);

        assertThrows(ConflictException.class, () -> legalManager.requestAccountDeletion(user, "istemiyorum"));
        verify(deletionDao, never()).save(any());
    }

    @Test
    void deletionIsBlockedWhileWorkIsStillOngoing() {
        when(jobAssignmentService.hasActiveWorkFor(any())).thenReturn(true);

        assertThrows(ConflictException.class, () -> legalManager.requestAccountDeletion(user, null));
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
}
