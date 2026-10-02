package com.exskylab.koala.webAPI;

import com.exskylab.koala.business.abstracts.StaffAuditService;
import com.exskylab.koala.core.constants.AuditMessages;
import com.exskylab.koala.core.dtos.audit.request.ClientAuditEventDto;
import com.exskylab.koala.core.dtos.audit.response.AuditEntryDto;
import com.exskylab.koala.core.dtos.audit.response.RecordedAuditEventDto;
import com.exskylab.koala.core.helpers.ClientIpResolver;
import com.exskylab.koala.core.security.StaffPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import com.exskylab.koala.core.dtos.common.PageDto;
import com.exskylab.koala.core.mappers.AuditEntryMapper;
import com.exskylab.koala.core.security.RequiresPermission;
import com.exskylab.koala.core.utilities.results.ErrorResult;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import com.exskylab.koala.entities.AdminAuditEntry;
import com.exskylab.koala.entities.AuditSubjectType;
import com.exskylab.koala.entities.StaffPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@Tag(name = "Denetim kaydı", description = "Yönetim panelinde kimin ne yaptığının değişmez kaydı.")
@RestController
@RequestMapping("/admin/audit-log")
public class AuditLogController {

    private final StaffAuditService staffAuditService;
    private final AuditEntryMapper auditEntryMapper;
    private final ClientIpResolver clientIpResolver;

    public AuditLogController(StaffAuditService staffAuditService, AuditEntryMapper auditEntryMapper,
                              ClientIpResolver clientIpResolver) {
        this.staffAuditService = staffAuditService;
        this.auditEntryMapper = auditEntryMapper;
        this.clientIpResolver = clientIpResolver;
    }

    @Operation(summary = "İstemci denetim olayı kaydet (CSV dışa aktarma)",
            description = "Yönetim paneli bir personel listesini CSV olarak dışa aktardığında bu uca bildirir; olay "
                    + "denetim kaydına tek satır olarak yazılır ve GET /admin/audit-log?action=export ile listelenir. "
                    + "Bugün yalnız action=EXPORT kabul edilir. Aktör her zaman token'daki personeldir; gövdede aktör "
                    + "alanı yoktur, gönderilirse yok sayılır. Uç her personele açıktır ama kayıt, çağıranın "
                    + "dışa aktarılan liste için taşıdığı izinle yazılır (JOB_ASSIGNMENTS payment.read, PAYOUTS "
                    + "payout.reconcile, REPORTS content.moderate, DISPUTES dispute.resolve): izin yoksa satır "
                    + "allowed=false olarak yazılır ve 403 döner. Satır mutating=false; hassasiyet listenin izninden "
                    + "gelir, piiIncluded=true ise en az PERSONAL_DATA olur. resourceType kaydın kaynak türü olur "
                    + "(job-assignments, payouts, reports, disputes), resourceId boş; filters, rowCount ve piiIncluded "
                    + "detay satırının requestBody alanında saklanır (6 ay). subjectType/subjectId verilirse kayıt o "
                    + "kullanıcının/şirketin zaman çizelgesinde görünür. Personel başına saatlik bütçe var (429); "
                    + "bütçeyi aşan çağrı kayda geçmez. Panel kaydı yazamazsa indirmeyi durdurmamalı, uyarı "
                    + "loglamalıdır.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Olay kaydedildi"),
            @ApiResponse(responseCode = "400", description = "Eylem/liste tanınmıyor, süzgeç özeti 1000 karakteri aşıyor "
                    + "ya da kişisel veri taşıyor (AUDIT_EVENT_FILTERS_PERSONAL_DATA), satır sayısı 0-1000 dışında, "
                    + "konu türü ile kimliği birlikte verilmemiş",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "403", description = "Çağıran dışa aktarılan listenin iznini taşımıyor "
                    + "(AUDIT_EVENT_LIST_PERMISSION_MISSING); ret de denetim kaydına yazılır",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "429", description = "Personel başına olay bütçesi doldu (AUDIT_EVENT_TOO_MANY)",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(action = "audit.event.record")
    @PostMapping("/events")
    public ResponseEntity<SuccessDataResult<RecordedAuditEventDto>> recordEvent(
            @RequestBody @Valid ClientAuditEventDto request,
            @AuthenticationPrincipal StaffPrincipal actor,
            HttpServletRequest httpRequest) {

        AdminAuditEntry entry = staffAuditService.recordClientEvent(actor, request,
                clientIpResolver.resolve(httpRequest), httpRequest.getHeader(HttpHeaders.USER_AGENT));

        return ResponseEntity.status(HttpStatus.CREATED).body(new SuccessDataResult<>(
                auditEntryMapper.toRecordedAuditEventDto(entry), AuditMessages.EVENT_RECORDED, HttpStatus.CREATED));
    }

    @Operation(summary = "Denetim kaydını listele",
            description = "Süzme tümüyle sunucuda yapılır; panel dönen sayfa içinde ikinci bir süzme uygulamaz. "
                    + "Çekirdek alanlar hiçbir zaman boş dönmez; detay alanları saklama süresi dolduğunda ya da "
                    + "KVKK silme talebiyle silindiği için null olabilir. subjectId verilirse kararın konusu "
                    + "(subjectId) ya da karşı tarafı (relatedSubjectId) o kimlik olan satırlar döner; subjectType "
                    + "(USER|COMPANY) verilirse eşleşme o türle sınırlanır, tek başına verilirse o türde konusu ya da "
                    + "karşı tarafı olan satırlar döner. Kullanıcı ve şirket zaman çizelgesi bu süzgeçle kurulur: "
                    + "personelin kimlik, silme talebi, görsel, IBAN, hakediş, atama, uyuşmazlık ve şikayet kararları "
                    + "kendi kaynak kimliklerinin altında yazılsa da konuya göre bulunur. Konu yazma satırlarında ve "
                    + "hassas (PERSONAL_DATA/FINANCIAL) okumalarda tutulur — ör. kimlik belgesi görüntüleme, iletişim "
                    + "bilgisiyle kullanıcı detayı; mutating=true kararları, mutating=false görüntülemeleri ayırır. "
                    + "Personel notu ve işareti yazımları da hedeflerinin konusunu taşır. Diğer süzgeçlerle kesişir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Denetim kaydı listelendi"),
            @ApiResponse(responseCode = "400", description = "Sıralama alanı tanınmıyor ya da subjectType/subjectId geçersiz",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(StaffPermission.AUDIT_READ)
    @GetMapping
    public ResponseEntity<SuccessDataResult<PageDto<AuditEntryDto>>> auditLog(
            @RequestParam(required = false) UUID actor,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String permission,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            @Parameter(description = "Kararın konusunun türü: USER ya da COMPANY.")
            @RequestParam(required = false) AuditSubjectType subjectType,
            @Parameter(description = "Kararın konusu ya da karşı tarafı olan kullanıcının veya şirketin kimliği.")
            @RequestParam(required = false) UUID subjectId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Boolean allowed,
            @Parameter(description = "true: yalnız yazma (karar) satırları, false: yalnız okuma satırları; verilmezse "
                    + "ikisi de. Zaman çizelgesi kararları görüntülemelerden bununla ayırır.")
            @RequestParam(required = false) Boolean mutating,
            @ParameterObject @PageableDefault(size = 20, sort = "occurredAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {

        Page<AdminAuditEntry> page = staffAuditService.search(
                actor, action, permission, resourceType, resourceId, subjectType, subjectId, from, to, allowed,
                mutating, pageable);

        return ResponseEntity.ok(new SuccessDataResult<>(
                PageDto.of(page, auditEntryMapper::toAuditEntryDto),
                AuditMessages.LOG_LISTED, HttpStatus.OK));
    }
}
