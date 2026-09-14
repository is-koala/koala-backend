package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.legal.request.ConsentDecisionDto;
import com.exskylab.koala.core.helpers.ClientContext;
import com.exskylab.koala.core.security.StaffPrincipal;
import com.exskylab.koala.core.dtos.legal.response.DeletionBlockerDto;
import com.exskylab.koala.entities.AccountDeletionRequest;
import com.exskylab.koala.entities.AgreementVersion;
import com.exskylab.koala.entities.ConsentRecord;
import com.exskylab.koala.entities.User;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.exskylab.koala.entities.AccountDeletionStatus;

public interface LegalService {

    List<AgreementVersion> getCurrentAgreements();

    List<AgreementVersion> getMandatoryAgreements();

    List<ConsentRecord> recordConsents(User user, List<ConsentDecisionDto> decisions, ClientContext client);

    void assertMandatoryConsentsGranted(List<ConsentDecisionDto> decisions);

    List<ConsentRecord> getConsentsOfUser(UUID userId);

    AccountDeletionRequest requestAccountDeletion(User user, String reason);

    AccountDeletionRequest getCurrentDeletionRequest(UUID userId);

    Page<AccountDeletionRequest> getDeletionRequests(AccountDeletionStatus status, String query, Pageable pageable);

    AccountDeletionRequest getDeletionRequest(UUID requestId);

    AccountDeletionRequest rejectDeletionRequest(StaffPrincipal actor, UUID requestId, String reason);

    List<UUID> findDueDeletionRequestIds(Instant dueBefore, Instant attemptBefore, Pageable pageable);

    boolean processDeletionRequest(UUID requestId);

    Optional<UUID> findOpenDeletionRequestId(UUID userId);

    Map<UUID, List<DeletionBlockerDto>> findDeletionBlockers(Collection<AccountDeletionRequest> requests);
}
