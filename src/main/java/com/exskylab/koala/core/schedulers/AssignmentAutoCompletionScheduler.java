package com.exskylab.koala.core.schedulers;

import com.exskylab.koala.business.abstracts.JobAssignmentService;
import com.exskylab.koala.core.properties.AutoCompletionProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class AssignmentAutoCompletionScheduler {

    private final Logger logger = LoggerFactory.getLogger(AssignmentAutoCompletionScheduler.class);

    private final JobAssignmentService jobAssignmentService;
    private final AutoCompletionProperties properties;

    public AssignmentAutoCompletionScheduler(JobAssignmentService jobAssignmentService,
                                             AutoCompletionProperties properties) {
        this.jobAssignmentService = jobAssignmentService;
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
                if (jobAssignmentService.autoCompleteAssignment(assignmentId)) {
                    completed++;
                }
            } catch (Exception e) {
                logger.error("Auto completion failed for assignment {}: {}", assignmentId, e.getMessage());
            }
        }

        logger.info("Auto completion run finished: {} of {} candidates completed.", completed, due.size());
    }
}
