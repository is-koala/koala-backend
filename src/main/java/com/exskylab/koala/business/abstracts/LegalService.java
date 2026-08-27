package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.legal.request.ConsentDecisionDto;
import com.exskylab.koala.core.helpers.ClientContext;
import com.exskylab.koala.entities.AccountDeletionRequest;
import com.exskylab.koala.entities.AgreementVersion;
import com.exskylab.koala.entities.ConsentRecord;
import com.exskylab.koala.entities.User;

import java.util.List;
import java.util.UUID;

public interface LegalService {

    List<AgreementVersion> getCurrentAgreements();

    List<AgreementVersion> getMandatoryAgreements();

    List<ConsentRecord> recordConsents(User user, List<ConsentDecisionDto> decisions, ClientContext client);

    void assertMandatoryConsentsGranted(List<ConsentDecisionDto> decisions);

    List<ConsentRecord> getConsentsOfUser(UUID userId);

    AccountDeletionRequest requestAccountDeletion(User user, String reason);
}
