package com.exskylab.koala.webAPI;

import com.exskylab.koala.business.abstracts.StaffNoteService;
import com.exskylab.koala.core.constants.StaffMessages;
import com.exskylab.koala.core.dtos.common.PageDto;
import com.exskylab.koala.core.dtos.staff.request.StaffNoteBodyDto;
import com.exskylab.koala.core.dtos.staff.response.StaffNoteDto;
import com.exskylab.koala.core.mappers.StaffNoteMapper;
import com.exskylab.koala.core.security.RequiresPermission;
import com.exskylab.koala.core.security.StaffPrincipal;
import com.exskylab.koala.core.utilities.results.ErrorResult;
import com.exskylab.koala.core.utilities.results.Result;
import com.exskylab.koala.core.utilities.results.SuccessDataResult;
import com.exskylab.koala.core.utilities.results.SuccessResult;
import com.exskylab.koala.entities.StaffNote;
import com.exskylab.koala.entities.StaffPermission;
import com.exskylab.koala.entities.StaffResourceType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Personel notları",
        description = "Personelin bir kullanıcı, şirket, atama, uyuşmazlık, şikayet ya da ilan hakkında bıraktığı "
                + "serbest metin notlar. Not gövdesi kişisel veri içerebilir: yalnız işin gerektirdiği bilgiyi yazın, "
                + "kimlik numarası, IBAN, parola gibi verileri nota koymayın; gövde user.read_pii istenmeden okunur ve "
                + "hiçbir zaman sunucu günlüğüne yazılmaz.")
@RestController
@RequestMapping("/admin/notes")
public class StaffNotesController {

    private final StaffNoteService staffNoteService;
    private final StaffNoteMapper staffNoteMapper;

    public StaffNotesController(StaffNoteService staffNoteService, StaffNoteMapper staffNoteMapper) {
        this.staffNoteService = staffNoteService;
        this.staffNoteMapper = staffNoteMapper;
    }

    @Operation(summary = "Kaynağın notlarını listele",
            description = "resourceType + resourceId çiftine bağlı notlar, en yeni önce (sort yalnız createdAt). "
                    + "Yazar kimliği ve görünen adı satırda taşınır. Kaynağın var olup olmadığı denetlenmez; "
                    + "not bırakılmamış ya da olmayan kaynak için boş sayfa döner.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notlar listelendi"),
            @ApiResponse(responseCode = "400", description = "resourceType/resourceId eksik ya da bilinmeyen sıralama alanı",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(StaffPermission.NOTE_READ)
    @GetMapping
    public ResponseEntity<SuccessDataResult<PageDto<StaffNoteDto>>> notes(
            @RequestParam StaffResourceType resourceType,
            @RequestParam UUID resourceId,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        Page<StaffNote> page = staffNoteService.listNotes(resourceType, resourceId, pageable);
        return ResponseEntity.ok(new SuccessDataResult<>(
                PageDto.of(page, staffNoteMapper.toNoteDtoList(page.getContent())),
                StaffMessages.NOTES_LISTED, HttpStatus.OK));
    }

    @Operation(summary = "Kaynağa not bırak",
            description = "Gövde en fazla 2000 karakter. Yazar istekteki personeldir; not yalnız yazarı ya da "
                    + "staff.manage taşıyan personel tarafından düzenlenip silinebilir.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Not eklendi"),
            @ApiResponse(responseCode = "400", description = "Gövde boş ya da 2000 karakteri aşıyor",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(value = StaffPermission.NOTE_WRITE, action = "staff.note.create")
    @PostMapping
    public ResponseEntity<SuccessDataResult<StaffNoteDto>> create(
            @AuthenticationPrincipal StaffPrincipal principal,
            @RequestParam StaffResourceType resourceType,
            @RequestParam UUID resourceId,
            @RequestBody @Valid StaffNoteBodyDto request) {
        StaffNote note = staffNoteService.createNote(principal, resourceType, resourceId, request.getBody());
        return ResponseEntity.status(HttpStatus.CREATED).body(new SuccessDataResult<>(
                staffNoteMapper.toNoteDto(note), StaffMessages.NOTE_CREATED, HttpStatus.CREATED));
    }

    @Operation(summary = "Notu düzenle",
            description = "Yalnız notun yazarı ya da staff.manage taşıyan personel. Başkasının notu, "
                    + "olmayan notla aynı 404 zarfını alır; editedAt damgalanır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Not güncellendi"),
            @ApiResponse(responseCode = "400", description = "Gövde boş ya da 2000 karakteri aşıyor",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "Not bulunamadı ya da bu personel düzenleyemez",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(value = StaffPermission.NOTE_WRITE, action = "staff.note.edit")
    @PatchMapping("/{noteId}")
    public ResponseEntity<SuccessDataResult<StaffNoteDto>> update(
            @AuthenticationPrincipal StaffPrincipal principal,
            @PathVariable UUID noteId,
            @RequestBody @Valid StaffNoteBodyDto request) {
        StaffNote note = staffNoteService.updateNote(principal, noteId, request.getBody());
        return ResponseEntity.ok(new SuccessDataResult<>(
                staffNoteMapper.toNoteDto(note), StaffMessages.NOTE_UPDATED, HttpStatus.OK));
    }

    @Operation(summary = "Notu sil",
            description = "Yalnız notun yazarı ya da staff.manage taşıyan personel. Silme kalıcıdır; notun "
                    + "gövdesi denetim kaydının detay satırında saklanır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Not silindi"),
            @ApiResponse(responseCode = "404", description = "Not bulunamadı ya da bu personel silemez",
                    content = @Content(schema = @Schema(implementation = ErrorResult.class)))
    })
    @RequiresPermission(value = StaffPermission.NOTE_WRITE, action = "staff.note.delete")
    @DeleteMapping("/{noteId}")
    public ResponseEntity<Result> delete(@AuthenticationPrincipal StaffPrincipal principal,
                                         @PathVariable UUID noteId) {
        staffNoteService.deleteNote(principal, noteId);
        return ResponseEntity.ok(new SuccessResult(StaffMessages.NOTE_DELETED, HttpStatus.OK));
    }
}
