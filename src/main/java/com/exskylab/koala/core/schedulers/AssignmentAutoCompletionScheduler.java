package com.exskylab.koala.core.schedulers;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.business.abstracts.DisputeService;
import com.exskylab.koala.business.abstracts.JobAssignmentService;
import com.exskylab.koala.core.properties.AutoCompletionProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class AssignmentAutoCompletionScheduler {

    private final JobAssignmentService jobAssignmentService;
    private final DisputeService disputeService;
    private final AutoCompletionProperties properties;

    public AssignmentAutoCompletionScheduler(JobAssignmentService jobAssignmentService,
                                             DisputeService disputeService,
                                             AutoCompletionProperties properties) {
        this.jobAssignmentService = jobAssignmentService;
        this.disputeService = disputeService;
        this.properties = properties;
    }

    @Scheduled(cron = "${assignment.auto-completion.cron:0 15 * * * *}")
    public void completeOverdueAssignments() {
        if (!properties.isEnabled()) {
            return;
        }

        List<UUID> due = jobAssignmentService.findAutoCompletableAssignmentIds();
        if (due.isEmpty()) {
            return;
        }

        int completed = 0;
        for (UUID assignmentId : due) {
            try {
                if (disputeService.hasActiveDispute(assignmentId)) {
                    log.info("Assignment {} has an open dispute, auto completion skipped.", assignmentId);
                    continue;
                }

                if (jobAssignmentService.autoCompleteAssignment(assignmentId)) {
                    completed++;
                }
            } catch (Exception e) {
                log.error("Auto completion failed for assignment {}: {}", assignmentId, e.getMessage());
            }
        }

        log.info("Auto completion run finished: {} of {} candidates completed.", completed, due.size());
    }
}
