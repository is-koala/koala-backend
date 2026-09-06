package com.exskylab.koala.core.schedulers;

import com.exskylab.koala.business.abstracts.StaffAuditService;
import com.exskylab.koala.core.properties.AuditRetentionProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AdminAuditRetentionScheduler {

    private final StaffAuditService staffAuditService;
    private final AuditRetentionProperties properties;

    public AdminAuditRetentionScheduler(StaffAuditService staffAuditService,
                                        AuditRetentionProperties properties) {
        this.staffAuditService = staffAuditService;
        this.properties = properties;
    }

    @Scheduled(cron = "${audit.retention.cron:0 30 3 * * *}")
    public void enforceRetention() {
        if (!properties.isEnabled()) {
            return;
        }

        try {
            int details = staffAuditService.purgeExpiredDetails();
            int entries = staffAuditService.purgeExpiredEntries();
            log.info("Audit retention run finished: {} detail rows and {} core rows removed.", details, entries);
        } catch (Exception exception) {
            log.error("Audit retention run failed", exception);
        }
    }
}
