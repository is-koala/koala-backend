package com.exskylab.koala.core.schedulers;

import com.exskylab.koala.business.abstracts.LegalService;
import com.exskylab.koala.core.properties.AccountDeletionProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class AccountAnonymizationScheduler {

    private final LegalService legalService;
    private final AccountDeletionProperties properties;

    public AccountAnonymizationScheduler(LegalService legalService, AccountDeletionProperties properties) {
        this.legalService = legalService;
        this.properties = properties;
    }

    @Scheduled(cron = "${account-deletion.cron:0 30 2 * * *}")
    public void processDueRequests() {
        if (!properties.isEnabled()) {
            return;
        }

        Instant now = Instant.now();
        Instant dueBefore = now.minus(properties.getWaitingPeriod());
        List<UUID> requestIds = legalService.findDueDeletionRequestIds(
                dueBefore, now, PageRequest.of(0, properties.getBatchSize()));
        int processed = 0;
        for (UUID requestId : requestIds) {
            try {
                if (legalService.processDeletionRequest(requestId)) {
                    processed++;
                }
            } catch (RuntimeException exception) {
                log.error("Account deletion request {} could not be processed", requestId, exception);
            }
        }
        log.info("Account anonymization run finished: {} of {} requests processed", processed, requestIds.size());
    }
}
