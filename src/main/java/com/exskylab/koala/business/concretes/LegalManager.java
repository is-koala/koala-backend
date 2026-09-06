package com.exskylab.koala.business.concretes;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.business.abstracts.LegalService;
import com.exskylab.koala.business.abstracts.StaffAuditService;
import com.exskylab.koala.core.constants.LegalMessages;
import com.exskylab.koala.core.dtos.legal.request.ConsentDecisionDto;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.helpers.ClientContext;
import com.exskylab.koala.dataAccess.AccountDeletionRequestDao;
import com.exskylab.koala.dataAccess.AgreementVersionDao;
import com.exskylab.koala.dataAccess.ConsentRecordDao;
import com.exskylab.koala.entities.*;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.business.abstracts.JobAssignmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class LegalManager implements LegalService {

    private final AgreementVersionDao agreementVersionDao;
    private final ConsentRecordDao consentRecordDao;
    private final AccountDeletionRequestDao accountDeletionRequestDao;
    private final JobAssignmentService jobAssignmentService;
    private final StaffAuditService staffAuditService;

    public LegalManager(AgreementVersionDao agreementVersionDao, ConsentRecordDao consentRecordDao,
                        AccountDeletionRequestDao accountDeletionRequestDao,
                        JobAssignmentService jobAssignmentService,
                        StaffAuditService staffAuditService) {
        this.agreementVersionDao = agreementVersionDao;
        this.consentRecordDao = consentRecordDao;
        this.accountDeletionRequestDao = accountDeletionRequestDao;
        this.jobAssignmentService = jobAssignmentService;
        this.staffAuditService = staffAuditService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgreementVersion> getCurrentAgreements() {
        return agreementVersionDao.findAllCurrent();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgreementVersion> getMandatoryAgreements() {
        return getCurrentAgreements().stream().filter(AgreementVersion::isMandatory).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public void assertMandatoryConsentsGranted(List<ConsentDecisionDto> decisions) {
        Set<UUID> granted = decisions == null ? Set.of()
                : decisions.stream()
                        .filter(d -> Boolean.TRUE.equals(d.getGranted()))
                        .map(ConsentDecisionDto::getAgreementVersionId)
                        .collect(Collectors.toSet());

        List<AgreementVersion> missing = getMandatoryAgreements().stream()
                .filter(agreement -> !granted.contains(agreement.getId()))
                .toList();

        if (!missing.isEmpty()) {
            log.warn("Registration blocked, mandatory consents missing: {}",
                    missing.stream().map(AgreementVersion::getType).toList());
            throw new ValidationException(LegalMessages.MANDATORY_CONSENT_MISSING);
        }
    }

    @Override
    @Transactional
    public List<ConsentRecord> recordConsents(User user, List<ConsentDecisionDto> decisions,
                                              ClientContext client) {
        if (decisions == null || decisions.isEmpty()) {
            return List.of();
        }

        Map<UUID, AgreementVersion> agreements = agreementVersionDao
                .findAllById(decisions.stream().map(ConsentDecisionDto::getAgreementVersionId).toList())
                .stream().collect(Collectors.toMap(AgreementVersion::getId, Function.identity()));

        List<ConsentRecord> records = new ArrayList<>();
        for (ConsentDecisionDto decision : decisions) {
            AgreementVersion agreement = agreements.get(decision.getAgreementVersionId());
            if (agreement == null) {
                throw new ResourceNotFoundException(LegalMessages.AGREEMENT_NOT_FOUND);
            }

            ConsentRecord record = new ConsentRecord();
            record.setUser(user);
            record.setAgreementVersion(agreement);
            record.setGranted(Boolean.TRUE.equals(decision.getGranted()));
            record.setRecordedAt(Instant.now());
            record.setIpAddress(client == null ? null : client.ipAddress());
            record.setUserAgent(truncate(client == null ? null : client.userAgent()));
            records.add(record);
        }

        List<ConsentRecord> saved = consentRecordDao.saveAll(records);
        log.info("Recorded {} consent decisions for user id: {}", saved.size(), user.getId());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConsentRecord> getConsentsOfUser(UUID userId) {
        return consentRecordDao.findAllByUserIdOrderByRecordedAtDesc(userId);
    }

    @Override
    @Transactional
    public AccountDeletionRequest requestAccountDeletion(User user, String reason) {
        accountDeletionRequestDao
                .findFirstByUserIdAndStatusOrderByRequestedAtDesc(user.getId(), AccountDeletionStatus.REQUESTED)
                .ifPresent(existing -> {
                    throw new ConflictException(LegalMessages.DELETION_ALREADY_REQUESTED);
                });

        assertNoOpenObligations(user.getId());

        AccountDeletionRequest request = new AccountDeletionRequest();
        request.setUser(user);
        request.setStatus(AccountDeletionStatus.REQUESTED);
        request.setReason(reason);
        request.setRequestedAt(Instant.now());

        AccountDeletionRequest saved = accountDeletionRequestDao.save(request);
        int erased = staffAuditService.eraseDetailsForResource(user.getId().toString());
        log.info("Account deletion requested for user id: {}, {} audit detail rows erased.",
                user.getId(), erased);
        return saved;
    }

    private void assertNoOpenObligations(UUID userId) {
        boolean hasEscrowedMoney = jobAssignmentService.hasMoneyInEscrowFor(userId);

        if (hasEscrowedMoney) {
            throw new ConflictException(LegalMessages.DELETION_BLOCKED_BY_ESCROW);
        }

        boolean hasActiveWork = jobAssignmentService.hasActiveWorkFor(userId);

        if (hasActiveWork) {
            throw new ConflictException(LegalMessages.DELETION_BLOCKED_BY_ACTIVE_WORK);
        }
    }

    private String truncate(String value) {
        return value == null ? null : value.substring(0, Math.min(value.length(), 512));
    }
}
