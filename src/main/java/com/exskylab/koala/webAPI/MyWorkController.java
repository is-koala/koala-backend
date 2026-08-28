package com.exskylab.koala.webAPI;

import com.exskylab.koala.business.abstracts.JobApplicationService;
import com.exskylab.koala.business.abstracts.JobAssignmentService;
import com.exskylab.koala.business.abstracts.MyWorkService;
import com.exskylab.koala.business.abstracts.NotificationService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.business.abstracts.SessionService;
import com.exskylab.koala.core.constants.MyWorkMessages;
import com.exskylab.koala.core.dtos.common.PageDto;
import com.exskylab.koala.core.dtos.jobApplication.response.MyApplicationDto;
import com.exskylab.koala.core.dtos.jobAssignment.response.MyAssignmentDto;
import com.exskylab.koala.core.dtos.myWork.response.WorkFeedDto;
import com.exskylab.koala.core.dtos.notification.response.NotificationDto;
import com.exskylab.koala.core.dtos.notification.response.NotificationFeedDto;
import com.exskylab.koala.core.dtos.session.response.SessionDto;
import com.exskylab.koala.core.mappers.NotificationMapper;
import com.exskylab.koala.core.mappers.SessionMapper;
import com.exskylab.koala.core.utilities.results.Result;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import com.exskylab.koala.core.utilities.results.SuccessResult;
import com.exskylab.koala.entities.ApplicationStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/users/me")
public class MyWorkController {

    private final JobApplicationService jobApplicationService;
    private final JobAssignmentService jobAssignmentService;
    private final MyWorkService myWorkService;
    private final SessionService sessionService;
    private final NotificationService notificationService;
    private final SecurityService securityService;
    private final SessionMapper sessionMapper;
    private final NotificationMapper notificationMapper;

    public MyWorkController(JobApplicationService jobApplicationService,
                            JobAssignmentService jobAssignmentService,
                            MyWorkService myWorkService,
                            SessionService sessionService,
                            NotificationService notificationService,
                            SecurityService securityService,
                            SessionMapper sessionMapper,
                            NotificationMapper notificationMapper) {
        this.jobApplicationService = jobApplicationService;
        this.jobAssignmentService = jobAssignmentService;
        this.myWorkService = myWorkService;
        this.sessionService = sessionService;
        this.notificationService = notificationService;
        this.securityService = securityService;
        this.sessionMapper = sessionMapper;
        this.notificationMapper = notificationMapper;
    }

    @GetMapping("/work")
    public ResponseEntity<SuccessDataResult<WorkFeedDto>> myWork(
            @RequestParam(defaultValue = "false") boolean asEmployer,
            @RequestParam(defaultValue = "50") int limit) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                myWorkService.getMyWork(asEmployer, limit), MyWorkMessages.WORK_LISTED, HttpStatus.OK));
    }

    @GetMapping("/applications")
    public ResponseEntity<SuccessDataResult<PageDto<MyApplicationDto>>> myApplications(
            @RequestParam(required = false) ApplicationStatus status,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                jobApplicationService.getMyApplications(status, pageable),
                MyWorkMessages.APPLICATIONS_LISTED, HttpStatus.OK));
    }

    @GetMapping("/assignments")
    public ResponseEntity<SuccessDataResult<PageDto<MyAssignmentDto>>> myAssignments(
            @RequestParam(defaultValue = "false") boolean asEmployer,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                jobAssignmentService.getMyAssignments(asEmployer, pageable),
                MyWorkMessages.ASSIGNMENTS_LISTED, HttpStatus.OK));
    }

    @GetMapping("/sessions")
    public ResponseEntity<SuccessDataResult<List<SessionDto>>> mySessions() {
        UUID currentSessionId = securityService.getAuthenticatedPrincipal().sessionId();

        List<SessionDto> sessions = sessionService.getMyActiveSessions().stream()
                .map(session -> sessionMapper.toSessionDto(session, session.getId().equals(currentSessionId)))
                .toList();

        return ResponseEntity.ok(new SuccessDataResult<>(
                sessions, MyWorkMessages.SESSIONS_LISTED, HttpStatus.OK));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Result> revokeSession(@PathVariable UUID sessionId) {
        sessionService.revokeSession(sessionId);
        return ResponseEntity.ok(new SuccessResult(MyWorkMessages.SESSION_REVOKED, HttpStatus.OK));
    }

    @DeleteMapping("/sessions")
    public ResponseEntity<SuccessDataResult<Integer>> revokeOtherSessions() {
        return ResponseEntity.ok(new SuccessDataResult<>(
                sessionService.revokeAllOtherSessions(), MyWorkMessages.SESSIONS_REVOKED, HttpStatus.OK));
    }

    @GetMapping("/notifications")
    public ResponseEntity<SuccessDataResult<NotificationFeedDto>> myNotifications(
            @PageableDefault(size = 20) Pageable pageable) {

        var page = notificationService.getMyNotifications(pageable);
        PageDto<NotificationDto> content = PageDto.of(page,
                notificationMapper.toNotificationDtoList(page.getContent()));

        return ResponseEntity.ok(new SuccessDataResult<>(
                new NotificationFeedDto(content, notificationService.myUnreadCount()),
                MyWorkMessages.NOTIFICATIONS_LISTED, HttpStatus.OK));
    }

    @PostMapping("/notifications/read")
    public ResponseEntity<SuccessDataResult<Integer>> markNotificationsRead() {
        return ResponseEntity.ok(new SuccessDataResult<>(
                notificationService.markAllRead(), MyWorkMessages.NOTIFICATIONS_READ, HttpStatus.OK));
    }
}
