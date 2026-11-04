package com.exskylab.koala.webAPI;

import com.exskylab.koala.business.abstracts.DisputeEvidenceScrubService;
import com.exskylab.koala.core.constants.DisputeMessages;
import com.exskylab.koala.core.dtos.dispute.request.EvidenceScrubRequestDto;
import com.exskylab.koala.core.dtos.dispute.response.EvidenceScrubReportDto;
import com.exskylab.koala.core.security.RequiresPermission;
import com.exskylab.koala.core.security.StaffPrincipal;
import com.exskylab.koala.core.utilities.results.ErrorResult;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import com.exskylab.koala.entities.StaffPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Uyuşmazlık yönetimi", description = "Açık uyuşmazlıkların admin tarafından çözülmesi.")
@RestController
@RequestMapping("/admin/dispute-evidence")
public class DisputeEvidenceScrubController {

    private final DisputeEvidenceScrubService disputeEvidenceScrubService;

    public DisputeEvidenceScrubController(DisputeEvidenceScrubService disputeEvidenceScrubService) {
        this.disputeEvidenceScrubService = disputeEvidenceScrubService;
    }

    @Operation(summary = "Saklanan görsel kanıtların metadata'sını temizle",
            description = "Bu sürümden önce yüklenmiş PNG/JPEG kanıtları EXIF/GPS, cihaz seri numarası ve metin parçalarıyla "
                    + "saklanıyordu. Uç en fazla limit (1-100, varsayılan 25) kanıtı tek tek işler: nesneyi okur, kayıtlı "
                    + "SHA-256 ile karşılaştırır, metadata'sız yeniden kodlar, yeni nesneye yazıp geri okuyarak doğrular, "
                    + "satırı yeni nesneye çevirir (önceki boyut ve özet satırda kalır) ve eski nesneyi silme kuyruğuna "
                    + "alır. Özeti tutmayan, okunamayan ya da çözülemeyen kanıta dokunulmaz ve failed listesinde döner; "
                    + "retryFailed=true onları yeniden dener. İdempotenttir: temizlenmiş kanıt bir daha işlenmez. "
                    + "remaining sıfır olana kadar yeniden çağrılır. PDF kanıtlar kapsam dışıdır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Parti işlendi"),
            @ApiResponse(responseCode = "400", description = "limit 1-100 aralığında değil ya da retryFailed "
                    + "mantıksal değer değil",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(value = StaffPermission.USER_DELETION_MANAGE, action = "dispute.evidence.scrub")
    @PostMapping("/metadata-scrub")
    public ResponseEntity<SuccessDataResult<EvidenceScrubReportDto>> scrubEvidenceMetadata(
            @AuthenticationPrincipal StaffPrincipal actor,
            @Valid @ParameterObject EvidenceScrubRequestDto request) {
        return ResponseEntity.ok(new SuccessDataResult<>(
                disputeEvidenceScrubService.scrubStoredEvidence(actor, request.getLimit(), request.retriesFailed()),
                DisputeMessages.EVIDENCE_SCRUBBED, HttpStatus.OK));
    }
}
