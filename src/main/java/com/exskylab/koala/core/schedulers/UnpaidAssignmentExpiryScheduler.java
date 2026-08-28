package com.exskylab.koala.core.schedulers;

import com.exskylab.koala.business.abstracts.CancellationService;
import com.exskylab.koala.business.abstracts.JobAssignmentService;
import com.exskylab.koala.core.properties.UnpaidExpiryProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class UnpaidAssignmentExpiryScheduler {

    private final JobAssignmentService jobAssignmentService;
    private final CancellationService cancellationService;
    private final UnpaidExpiryProperties properties;

    public UnpaidAssignmentExpiryScheduler(JobAssignmentService jobAssignmentService,
                                           CancellationService cancellationService,
                                           UnpaidExpiryProperties properties) {
        this.jobAssignmentService = jobAssignmentService;
        this.cancellationService = cancellationService;
        this.properties = properties;
    }

    @Scheduled(cron = "${assignment.unpaid-expiry.cron:0 45 * * * *}")
    public void expireUnpaidAssignments() {
        if (!properties.isEnabled()) {
            return;
        }

        List<UUID> due = jobAssignmentService.findUnpaidExpiringAssignmentIds();
        if (due.isEmpty()) {
            return;
        }

        int expired = 0;
        for (UUID assignmentId : due) {
            try {
                if (cancellationService.expireUnpaidAssignment(assignmentId)) {
                    expired++;
                }
            } catch (Exception e) {
                log.error("Unpaid expiry failed for assignment {}: {}", assignmentId, e.getMessage());
            }
        }

        log.info("Unpaid expiry run finished: {} of {} candidates expired.", expired, due.size());
    }
}
