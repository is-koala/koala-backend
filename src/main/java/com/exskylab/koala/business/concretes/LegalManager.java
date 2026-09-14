package com.exskylab.koala.business.concretes;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.business.abstracts.LegalService;
import com.exskylab.koala.business.abstracts.StaffAuditService;
import com.exskylab.koala.core.constants.LegalMessages;
import com.exskylab.koala.core.dtos.legal.request.ConsentDecisionDto;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.helpers.ClientContext;
import com.exskylab.koala.core.helpers.PageableSanitizer;
import com.exskylab.koala.dataAccess.AccountDeletionRequestDao;
import com.exskylab.koala.dataAccess.AgreementVersionDao;
import com.exskylab.koala.dataAccess.ConsentRecordDao;
import com.exskylab.koala.core.dtos.legal.response.DeletionBlockerDto;
import com.exskylab.koala.core.security.StaffPrincipal;
import com.exskylab.koala.dataAccess.PartyLink;
import com.exskylab.koala.entities.*;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.business.abstracts.JobAssignmentService;
import com.exskylab.koala.business.abstracts.DisputeService;
import com.exskylab.koala.business.abstracts.PaymentService;
import com.exskylab.koala.core.privacy.AccountAnonymizer;
import com.exskylab.koala.core.helpers.StaffSearchQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class LegalManager implements LegalService {

    private static final java.time.Duration DELETION_RETRY_DELAY = java.time.Duration.ofDays(1);

    private static final Set<String> DELETION_SORT_FIELDS = Set.of(
            "id", "subjectId", "status", "requestedAt", "processedAt");

    private final AgreementVersionDao agreementVersionDao;
    private final ConsentRecordDao consentRecordDao;
    private final AccountDeletionRequestDao accountDeletionRequestDao;
    private final JobAssignmentService jobAssignmentService;
    private final StaffAuditService staffAuditService;
    private final DisputeService disputeService;
    private final PaymentService paymentService;
    private final AccountAnonymizer accountAnonymizer;
    private final PageableSanitizer pageableSanitizer = new PageableSanitizer();

    public LegalManager(AgreementVersionDao agreementVersionDao, ConsentRecordDao consentRecordDao,
                        AccountDeletionRequestDao accountDeletionRequestDao,
                        JobAssignmentService jobAssignmentService, DisputeService disputeService,
                        PaymentService paymentService, AccountAnonymizer accountAnonymizer,
                        StaffAuditService staffAuditService) {
        this.agreementVersionDao = agreementVersionDao;
        this.consentRecordDao = consentRecordDao;
        this.accountDeletionRequestDao = accountDeletionRequestDao;
        this.jobAssignmentService = jobAssignmentService;
        this.staffAuditService = staffAuditService;
        this.disputeService = disputeService;
        this.paymentService = paymentService;
        this.accountAnonymizer = accountAnonymizer;
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

        AccountDeletionRequest request = new AccountDeletionRequest();
        request.setUser(user);
        request.setSubjectId(user.getId());
        request.setStatus(AccountDeletionStatus.REQUESTED);
        request.setReason(reason);
        request.setRequestedAt(Instant.now());
        String blockedReason = findBlockedReason(user.getId());
        request.setBlockedReason(blockedReason);
        request.setNextAttemptAt(blockedReason == null ? null : Instant.now().plus(DELETION_RETRY_DELAY));

        AccountDeletionRequest saved = accountDeletionRequestDao.save(request);
        int erased = staffAuditService.eraseDetailsForResource(user.getId().toString());
        log.info("Account deletion requested for user id: {}, {} audit detail rows erased.",
                user.getId(), erased);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public AccountDeletionRequest getCurrentDeletionRequest(UUID userId) {
        return accountDeletionRequestDao.findFirstByUserIdOrderByRequestedAtDesc(userId)
                .orElseThrow(() -> new ResourceNotFoundException(LegalMessages.DELETION_NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AccountDeletionRequest> getDeletionRequests(AccountDeletionStatus status, String query,
                                                            Pageable pageable) {
        pageableSanitizer.rejectUnknownSort(pageable, DELETION_SORT_FIELDS);
        return accountDeletionRequestDao.search(status, StaffSearchQuery.normalize(query), pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public AccountDeletionRequest getDeletionRequest(UUID requestId) {
        return accountDeletionRequestDao.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException(LegalMessages.DELETION_NOT_FOUND));
    }

    @Override
    @Transactional
    public AccountDeletionRequest rejectDeletionRequest(StaffPrincipal actor, UUID requestId, String reason) {
        AccountDeletionRequest request = lockedRequest(requestId);
        if (request.getStatus() != AccountDeletionStatus.REQUESTED) {
            throw new ConflictException(LegalMessages.DELETION_ALREADY_PROCESSED);
        }
        request.setStatus(AccountDeletionStatus.REJECTED);
        request.setRejectionReason(reason);
        request.setRejectedByStaffId(actor == null ? null : actor.staffId());
        request.setProcessedAt(Instant.now());
        log.info("Account deletion request {} rejected by {}", requestId,
                actor == null ? "bridge admin" : "staff " + actor.staffId());
        return accountDeletionRequestDao.save(request);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findDueDeletionRequestIds(Instant dueBefore, Instant attemptBefore, Pageable pageable) {
        pageableSanitizer.rejectUnknownSort(pageable, DELETION_SORT_FIELDS);
        return accountDeletionRequestDao.findDueIds(
                AccountDeletionStatus.REQUESTED, dueBefore, attemptBefore, pageable);
    }

    @Override
    @Transactional
    public boolean processDeletionRequest(UUID requestId) {
        AccountDeletionRequest request = lockedRequest(requestId);
        if (request.getStatus() != AccountDeletionStatus.REQUESTED) {
            return false;
        }

        String blockedReason = findBlockedReason(request.getUser().getId());
        request.setBlockedReason(blockedReason);
        if (blockedReason != null) {
            request.setNextAttemptAt(Instant.now().plus(DELETION_RETRY_DELAY));
            accountDeletionRequestDao.save(request);
            return false;
        }

        accountAnonymizer.anonymize(request.getUser().getId(), request.getId());
        request.setStatus(AccountDeletionStatus.ANONYMIZED);
        request.setUser(null);
        request.setReason(null);
        request.setBlockedReason(null);
        request.setNextAttemptAt(null);
        request.setProcessedAt(Instant.now());
        accountDeletionRequestDao.save(request);
        return true;
    }

    private AccountDeletionRequest lockedRequest(UUID requestId) {
        return accountDeletionRequestDao.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResourceNotFoundException(LegalMessages.DELETION_NOT_FOUND));
    }

    private String findBlockedReason(UUID userId) {
        return blockersOf(Set.of(userId)).getOrDefault(userId, List.of()).stream()
                .findFirst()
                .map(blocker -> blocker.type().getMessageKey())
                .orElse(null);
    }

    private String truncate(String value) {
        return value == null ? null : value.substring(0, Math.min(value.length(), 512));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> findOpenDeletionRequestId(UUID userId) {
        return accountDeletionRequestDao
                .findFirstByUserIdAndStatusOrderByRequestedAtDesc(userId, AccountDeletionStatus.REQUESTED)
                .map(AccountDeletionRequest::getId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, List<DeletionBlockerDto>> findDeletionBlockers(Collection<AccountDeletionRequest> requests) {
        Map<UUID, UUID> userByRequest = new LinkedHashMap<>();
        for (AccountDeletionRequest request : requests) {
            if (request.getStatus() == AccountDeletionStatus.REQUESTED && request.getUser() != null) {
                userByRequest.put(request.getId(), request.getUser().getId());
            }
        }
        if (userByRequest.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<DeletionBlockerDto>> byUser = blockersOf(new HashSet<>(userByRequest.values()));

        Map<UUID, List<DeletionBlockerDto>> byRequest = new HashMap<>();
        userByRequest.forEach((requestId, userId) ->
                byRequest.put(requestId, byUser.getOrDefault(userId, List.of())));
        return byRequest;
    }

    private Map<UUID, List<DeletionBlockerDto>> blockersOf(Set<UUID> userIds) {
        Map<UUID, List<DeletionBlockerDto>> byUser = new HashMap<>();
        collectBlockers(byUser, userIds, DeletionBlockerType.PENDING_PAYMENT,
                paymentService.findPendingLinksForUsers(userIds));
        collectBlockers(byUser, userIds, DeletionBlockerType.ESCROW,
                jobAssignmentService.findEscrowLinksForUsers(userIds));
        collectBlockers(byUser, userIds, DeletionBlockerType.ACTIVE_WORK,
                jobAssignmentService.findActiveWorkLinksForUsers(userIds));
        collectBlockers(byUser, userIds, DeletionBlockerType.DISPUTE,
                disputeService.findActiveLinksForUsers(userIds));
        return byUser;
    }

    private static void collectBlockers(Map<UUID, List<DeletionBlockerDto>> byUser, Set<UUID> userIds,
                                        DeletionBlockerType type, List<PartyLink> links) {
        for (PartyLink link : links) {
            for (UUID party : List.of(link.getWorkerId(), link.getEmployerId())) {
                if (userIds.contains(party)) {
                    List<DeletionBlockerDto> blockers = byUser.computeIfAbsent(party, ignored -> new ArrayList<>());
                    DeletionBlockerDto blocker = new DeletionBlockerDto(type, link.getId());
                    if (!blockers.contains(blocker)) {
                        blockers.add(blocker);
                    }
                }
            }
        }
    }
}
