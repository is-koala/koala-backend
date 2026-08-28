package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.JobApplicationService;
import com.exskylab.koala.business.abstracts.JobAssignmentService;
import com.exskylab.koala.business.abstracts.MyWorkService;
import com.exskylab.koala.core.dtos.jobApplication.response.MyApplicationDto;
import com.exskylab.koala.core.dtos.jobAssignment.response.MyAssignmentDto;
import com.exskylab.koala.core.dtos.myWork.response.WorkFeedDto;
import com.exskylab.koala.core.dtos.myWork.response.WorkItemDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@Slf4j
public class MyWorkManager implements MyWorkService {

    private static final int MAX_LIMIT = 100;
    private static final int MIN_LIMIT = 1;

    private final JobApplicationService jobApplicationService;
    private final JobAssignmentService jobAssignmentService;

    public MyWorkManager(JobApplicationService jobApplicationService,
                         JobAssignmentService jobAssignmentService) {
        this.jobApplicationService = jobApplicationService;
        this.jobAssignmentService = jobAssignmentService;
    }

    @Override
    @Transactional(readOnly = true)
    public WorkFeedDto getMyWork(boolean asEmployer, int requestedLimit) {
        int limit = Math.clamp(requestedLimit, MIN_LIMIT, MAX_LIMIT);
        log.debug("Building unified work feed asEmployer={} limit={}", asEmployer, limit);

        List<MyApplicationDto> applications = asEmployer
                ? List.of()
                : jobApplicationService.getMyApplications(null, PageRequest.of(0, limit)).content();

        List<MyAssignmentDto> assignments =
                jobAssignmentService.getMyAssignments(asEmployer, PageRequest.of(0, limit)).content();

        List<WorkItemDto> items = new ArrayList<>(applications.size() + assignments.size());
        applications.forEach(application -> items.add(WorkItemDto.of(application)));
        assignments.forEach(assignment -> items.add(WorkItemDto.of(assignment)));

        items.sort(Comparator.comparing(WorkItemDto::occurredAt,
                Comparator.nullsLast(Comparator.reverseOrder())));

        return new WorkFeedDto(items.stream().limit(limit).toList(),
                applications.size(), assignments.size());
    }
}
