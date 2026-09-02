package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.utilities.results.ErrorResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

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
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "İşlerim", description = "Başvurular, atamalar, oturumlar ve bildirimler.")
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

    @Operation(summary = "Birleşik işlerim akışı",
            description = "Başvuru ve atamaları tek bir zaman çizelgesinde birleştirir; her satır kind alanıyla APPLICATION ya da ASSIGNMENT olarak etiketlidir. limit varsayılan 50, 1 ile 100 arasına kırpılır, reddedilmez. asEmployer true iken başvuru döndürülmez; işverenin başvurusu olmaz, kendi ilanına gelenler ilan altındadır. Sayfalama yoktur, hepsini görmek için sayfalı uçlar kullanılır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Akış döndürüldü")
    })
    @GetMapping("/work")
    public ResponseEntity<SuccessDataResult<WorkFeedDto>> myWork(
            @RequestParam(defaultValue = "false") boolean asEmployer,
            @RequestParam(defaultValue = "50") int limit) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                myWorkService.getMyWork(asEmployer, limit), MyWorkMessages.WORK_LISTED, HttpStatus.OK));
    }

    @Operation(summary = "Başvurularım",
            description = "status parametresiyle süzülebilir. Sayfalıdır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Başvurular listelendi"),
            @ApiResponse(responseCode = "400", description = "Sıralama alanı desteklenmiyor",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @GetMapping("/applications")
    public ResponseEntity<SuccessDataResult<PageDto<MyApplicationDto>>> myApplications(
            @RequestParam(required = false) ApplicationStatus status,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                jobApplicationService.getMyApplications(status, pageable),
                MyWorkMessages.APPLICATIONS_LISTED, HttpStatus.OK));
    }

    @Operation(summary = "Atamalarım",
            description = "asEmployer true ile işveren tarafı listelenir. Sayfalıdır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Atamalar listelendi"),
            @ApiResponse(responseCode = "400", description = "Sıralama alanı desteklenmiyor",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @GetMapping("/assignments")
    public ResponseEntity<SuccessDataResult<PageDto<MyAssignmentDto>>> myAssignments(
            @RequestParam(defaultValue = "false") boolean asEmployer,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(new SuccessDataResult<>(
                jobAssignmentService.getMyAssignments(asEmployer, pageable),
                MyWorkMessages.ASSIGNMENTS_LISTED, HttpStatus.OK));
    }

    @Operation(summary = "Açık oturumlarım",
            description = "İstek atan oturum current bayrağıyla işaretlidir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Oturumlar listelendi")
    })
    @GetMapping("/sessions")
    public ResponseEntity<SuccessDataResult<List<SessionDto>>> mySessions() {
        UUID currentSessionId = securityService.getAuthenticatedPrincipal().sessionId();

        List<SessionDto> sessions = sessionService.getMyActiveSessions().stream()
                .map(session -> sessionMapper.toSessionDto(session, session.getId().equals(currentSessionId)))
                .toList();

        return ResponseEntity.ok(new SuccessDataResult<>(
                sessions, MyWorkMessages.SESSIONS_LISTED, HttpStatus.OK));
    }

    @Operation(summary = "Bir oturumu kapat",
            description = "Başkasının oturumunu kapatmaya çalışmak 404 döner.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Oturum kapatıldı"),
            @ApiResponse(responseCode = "404", description = "Oturum bulunamadı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Result> revokeSession(@PathVariable UUID sessionId) {
        sessionService.revokeSession(sessionId);
        return ResponseEntity.ok(new SuccessResult(MyWorkMessages.SESSION_REVOKED, HttpStatus.OK));
    }

    @Operation(summary = "Diğer tüm oturumları kapat",
            description = "İstek atan oturum hariç hepsi kapatılır; kaç oturumun kapandığı döner.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Oturumlar kapatıldı")
    })
    @DeleteMapping("/sessions")
    public ResponseEntity<SuccessDataResult<Integer>> revokeOtherSessions() {
        return ResponseEntity.ok(new SuccessDataResult<>(
                sessionService.revokeAllOtherSessions(), MyWorkMessages.SESSIONS_REVOKED, HttpStatus.OK));
    }

    @Operation(summary = "Bildirimlerim",
            description = "Okunmamış sayısıyla birlikte döner. Sayfalıdır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bildirimler listelendi")
    })
    @GetMapping("/notifications")
    public ResponseEntity<SuccessDataResult<NotificationFeedDto>> myNotifications(
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {

        var page = notificationService.getMyNotifications(pageable);
        PageDto<NotificationDto> content = PageDto.of(page,
                notificationMapper.toNotificationDtoList(page.getContent()));

        return ResponseEntity.ok(new SuccessDataResult<>(
                new NotificationFeedDto(content, notificationService.myUnreadCount()),
                MyWorkMessages.NOTIFICATIONS_LISTED, HttpStatus.OK));
    }

    @Operation(summary = "Tüm bildirimleri okundu işaretle",
            description = "Kaç bildirimin işaretlendiği döner.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bildirimler okundu işaretlendi")
    })
    @PostMapping("/notifications/read")
    public ResponseEntity<SuccessDataResult<Integer>> markNotificationsRead() {
        return ResponseEntity.ok(new SuccessDataResult<>(
                notificationService.markAllRead(), MyWorkMessages.NOTIFICATIONS_READ, HttpStatus.OK));
    }

    @Operation(summary = "Tek bildirimi okundu işaretle",
            description = "Zaten okunmuş ya da başkasına ait bir bildirimde sessizce geçer; hata dönmez.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bildirim okundu işaretlendi")
    })
    @PostMapping("/notifications/{notificationId}/read")
    public ResponseEntity<SuccessResult> markNotificationRead(@PathVariable UUID notificationId) {
        notificationService.markRead(notificationId);

        return ResponseEntity.ok(new SuccessResult(MyWorkMessages.NOTIFICATION_READ, HttpStatus.OK));
    }
}
