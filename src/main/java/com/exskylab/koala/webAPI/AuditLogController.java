package com.exskylab.koala.webAPI;

import com.exskylab.koala.business.abstracts.StaffAuditService;
import com.exskylab.koala.core.constants.AuditMessages;
import com.exskylab.koala.core.dtos.audit.response.AuditEntryDto;
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

    public AuditLogController(StaffAuditService staffAuditService, AuditEntryMapper auditEntryMapper) {
        this.staffAuditService = staffAuditService;
        this.auditEntryMapper = auditEntryMapper;
    }

    @Operation(summary = "Denetim kaydını listele",
            description = "Süzme tümüyle sunucuda yapılır; panel dönen sayfa içinde ikinci bir süzme uygulamaz. "
                    + "Çekirdek alanlar hiçbir zaman boş dönmez; detay alanları saklama süresi dolduğunda ya da "
                    + "KVKK silme talebiyle silindiği için null olabilir. subjectId verilirse kararın konusu "
                    + "(subjectId) ya da karşı tarafı (relatedSubjectId) o kimlik olan satırlar döner; subjectType "
                    + "(USER|COMPANY) verilirse eşleşme o türle sınırlanır, tek başına verilirse o türde konusu ya da "
                    + "karşı tarafı olan satırlar döner. Kullanıcı ve şirket zaman çizelgesi bu süzgeçle kurulur: "
                    + "personelin kimlik, silme talebi, görsel, IBAN, hakediş, atama, uyuşmazlık ve şikayet kararları "
                    + "kendi kaynak kimliklerinin altında yazılsa da konuya göre bulunur. Konu yalnız yazma "
                    + "satırlarında tutulur; okuma satırları bu süzgeçle dönmez. Diğer süzgeçlerle kesişir.")
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
            @ParameterObject @PageableDefault(size = 20, sort = "occurredAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {

        Page<AdminAuditEntry> page = staffAuditService.search(
                actor, action, permission, resourceType, resourceId, subjectType, subjectId, from, to, allowed,
                pageable);

        return ResponseEntity.ok(new SuccessDataResult<>(
                PageDto.of(page, auditEntryMapper::toAuditEntryDto),
                AuditMessages.LOG_LISTED, HttpStatus.OK));
    }
}
