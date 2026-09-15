package com.exskylab.koala.webAPI;

import com.exskylab.koala.business.abstracts.StaffNoteService;
import com.exskylab.koala.core.constants.StaffMessages;
import com.exskylab.koala.core.dtos.staff.request.SetStaffFlagDto;
import com.exskylab.koala.core.dtos.staff.response.StaffFlagDto;
import com.exskylab.koala.core.helpers.StaffFlagLookup;
import com.exskylab.koala.core.mappers.StaffNoteMapper;
import com.exskylab.koala.core.security.RequiresPermission;
import com.exskylab.koala.core.security.StaffPrincipal;
import com.exskylab.koala.core.utilities.results.ErrorResult;
import com.exskylab.koala.core.utilities.results.Result;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import com.exskylab.koala.core.utilities.results.SuccessResult;
import com.exskylab.koala.entities.StaffFlag;
import com.exskylab.koala.entities.StaffFlagKind;
import com.exskylab.koala.entities.StaffPermission;
import com.exskylab.koala.entities.StaffResourceType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Personel işaretleri",
        description = "Bir kaynağa konan RISK / VIP / WATCH / OTHER işaretleri. Aynı kaynakta her türden en fazla "
                + "bir etkin işaret bulunur; kaldırılan işaret clearedAt ile kapanır, listeler yalnız etkin olanları "
                + "döndürür.")
@RestController
@RequestMapping("/admin/flags")
public class StaffFlagsController {

    private final StaffNoteService staffNoteService;
    private final StaffNoteMapper staffNoteMapper;

    public StaffFlagsController(StaffNoteService staffNoteService, StaffNoteMapper staffNoteMapper) {
        this.staffNoteService = staffNoteService;
        this.staffNoteMapper = staffNoteMapper;
    }

    @Operation(summary = "Etkin işaretleri oku",
            description = "Tek kaynak için resourceId, liste rozetleri için resourceIds (virgülle ayrılmış, en fazla "
                    + "200) verilir; ikisi birlikte de verilebilir. Yanıt düz bir listedir, her satır resourceId "
                    + "taşır; panel satırları kaynağa göre gruplar. Tek sorgu ile okunur.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "İşaretler listelendi"),
            @ApiResponse(responseCode = "400", description = "Kaynak verilmemiş ya da 200'den fazla kaynak istenmiş",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(StaffPermission.FLAG_MANAGE)
    @GetMapping
    public ResponseEntity<SuccessDataResult<List<StaffFlagDto>>> flags(
            @RequestParam StaffResourceType resourceType,
            @RequestParam(required = false) UUID resourceId,
            @RequestParam(required = false) List<UUID> resourceIds) {
        List<StaffFlag> flags = staffNoteService.listActiveFlags(resourceType,
                StaffFlagLookup.resourceIdsOf(resourceId, resourceIds));
        return ResponseEntity.ok(new SuccessDataResult<>(
                staffNoteMapper.toFlagDtoList(flags), StaffMessages.FLAGS_LISTED, HttpStatus.OK));
    }

    @Operation(summary = "İşaret koy",
            description = "İdempotenttir: kaynakta aynı türden etkin bir işaret varsa etiketi, koyan personeli ve "
                    + "setAt damgasını günceller, yeni satır açmaz. Etiket isteğe bağlı, en fazla 120 karakter.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "İşaret kondu"),
            @ApiResponse(responseCode = "400", description = "İşaret türü eksik ya da etiket 120 karakteri aşıyor",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(value = StaffPermission.FLAG_MANAGE, action = "staff.flag.set")
    @PutMapping
    public ResponseEntity<SuccessDataResult<StaffFlagDto>> set(
            @AuthenticationPrincipal StaffPrincipal principal,
            @RequestParam StaffResourceType resourceType,
            @RequestParam UUID resourceId,
            @RequestBody @Valid SetStaffFlagDto request) {
        StaffFlag flag = staffNoteService.setFlag(principal, resourceType, resourceId,
                request.getKind(), request.getLabel());
        return ResponseEntity.ok(new SuccessDataResult<>(
                staffNoteMapper.toFlagDto(flag), StaffMessages.FLAG_SET, HttpStatus.OK));
    }

    @Operation(summary = "İşareti kaldır",
            description = "Kaynaktaki verilen türden etkin işareti clearedAt ile kapatır; satır silinmez. "
                    + "Etkin işaret yoksa 404.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "İşaret kaldırıldı"),
            @ApiResponse(responseCode = "404", description = "Bu kaynakta bu türden etkin işaret yok",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(value = StaffPermission.FLAG_MANAGE, action = "staff.flag.clear")
    @DeleteMapping
    public ResponseEntity<Result> clear(@AuthenticationPrincipal StaffPrincipal principal,
                                        @RequestParam StaffResourceType resourceType,
                                        @RequestParam UUID resourceId,
                                        @RequestParam StaffFlagKind kind) {
        staffNoteService.clearFlag(principal, resourceType, resourceId, kind);
        return ResponseEntity.ok(new SuccessResult(StaffMessages.FLAG_CLEARED, HttpStatus.OK));
    }
}
