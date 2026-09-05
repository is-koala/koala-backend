package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.JobApplicationService;
import com.exskylab.koala.business.abstracts.JobAssignmentService;
import com.exskylab.koala.core.dtos.common.PageDto;
import com.exskylab.koala.core.dtos.job.response.JobSummaryDto;
import com.exskylab.koala.core.dtos.jobApplication.response.MyApplicationDto;
import com.exskylab.koala.core.dtos.jobAssignment.response.MyAssignmentDto;
import com.exskylab.koala.core.dtos.myWork.response.WorkFeedDto;
import com.exskylab.koala.core.dtos.myWork.response.WorkItemDto;
import com.exskylab.koala.entities.ApplicationStatus;
import com.exskylab.koala.entities.AssignmentStatus;
import com.exskylab.koala.entities.WorkItemKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

class MyWorkFeedTest {

    private JobApplicationService jobApplicationService;
    private JobAssignmentService jobAssignmentService;
    private MyWorkManager manager;

    private final Instant now = Instant.parse("2026-08-28T10:00:00Z");

    @BeforeEach
    void setUp() {
        jobApplicationService = mock(JobApplicationService.class);
        jobAssignmentService = mock(JobAssignmentService.class);
        manager = new MyWorkManager(jobApplicationService, jobAssignmentService);

        when(jobApplicationService.getMyApplications(any(), any())).thenReturn(page(List.of()));
        when(jobAssignmentService.getMyAssignments(anyBoolean(), any())).thenReturn(page(List.of()));
    }

    @Test
    void applicationsAndAssignmentsArriveInOneTaggedTimeline() {
        when(jobApplicationService.getMyApplications(any(), any()))
                .thenReturn(page(List.of(application(now.minus(2, ChronoUnit.HOURS)))));
        when(jobAssignmentService.getMyAssignments(anyBoolean(), any()))
                .thenReturn(page(List.of(assignment(now.minus(1, ChronoUnit.HOURS)))));

        WorkFeedDto feed = manager.getMyWork(false, 50);

        assertEquals(2, feed.items().size());
        assertEquals(1, feed.applicationCount());
        assertEquals(1, feed.assignmentCount());
        assertEquals(WorkItemKind.ASSIGNMENT, feed.items().getFirst().kind());
        assertEquals(WorkItemKind.APPLICATION, feed.items().getLast().kind());
    }

    @Test
    void theNewestItemComesFirst() {
        when(jobApplicationService.getMyApplications(any(), any()))
                .thenReturn(page(List.of(application(now), application(now.minus(5, ChronoUnit.DAYS)))));

        WorkFeedDto feed = manager.getMyWork(false, 50);

        assertEquals(now, feed.items().getFirst().occurredAt());
    }

    @Test
    void eachItemCarriesOnlyItsOwnPayload() {
        when(jobApplicationService.getMyApplications(any(), any()))
                .thenReturn(page(List.of(application(now))));

        WorkItemDto item = manager.getMyWork(false, 50).items().getFirst();

        assertNotNull(item.application());
        assertNull(item.assignment());
        assertEquals(ApplicationStatus.APPLIED.name(), item.status());
        assertNotNull(item.job());
    }

    @Test
    void theEmployerSideCarriesNoApplications() {
        manager.getMyWork(true, 50);

        verify(jobApplicationService, never()).getMyApplications(any(), any());
        verify(jobAssignmentService).getMyAssignments(eq(true), any(Pageable.class));
    }

    @Test
    void theLimitTruncatesTheMergedTimeline() {
        when(jobApplicationService.getMyApplications(any(), any()))
                .thenReturn(page(IntStream.range(0, 3)
                        .mapToObj(i -> application(now.minus(i, ChronoUnit.HOURS))).toList()));
        when(jobAssignmentService.getMyAssignments(anyBoolean(), any()))
                .thenReturn(page(IntStream.range(0, 3)
                        .mapToObj(i -> assignment(now.minus(i, ChronoUnit.DAYS))).toList()));

        assertEquals(4, manager.getMyWork(false, 4).items().size());
    }

    @Test
    void anAbsurdLimitIsClampedInsteadOfRejected() {
        manager.getMyWork(false, 100_000);

        var captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(jobAssignmentService).getMyAssignments(anyBoolean(), captor.capture());
        assertEquals(100, captor.getValue().getPageSize());
    }

    @Test
    void aZeroLimitStillReturnsOneRow() {
        var captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);

        manager.getMyWork(false, 0);

        verify(jobAssignmentService).getMyAssignments(anyBoolean(), captor.capture());
        assertEquals(1, captor.getValue().getPageSize());
    }

    private static <T> PageDto<T> page(List<T> content) {
        return new PageDto<>(content, 0, content.size(), content.size(), 1, true, true);
    }

    private static MyApplicationDto application(Instant at) {
        return new MyApplicationDto(UUID.randomUUID(), job(), ApplicationStatus.APPLIED,
                null, null, at, null, null);
    }

    private static MyAssignmentDto assignment(Instant at) {
        return new MyAssignmentDto(UUID.randomUUID(), job(), null, AssignmentStatus.CONFIRMED,
                null, at, null, 0, null, null, null);
    }

    private static JobSummaryDto job() {
        JobSummaryDto job = new JobSummaryDto();
        job.setId(UUID.randomUUID());
        return job;
    }
}
